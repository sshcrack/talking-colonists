package me.sshcrack.mc_talking.manager.music;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.google.gson.JsonObject;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import me.sshcrack.mc_talking.manager.GeminiStream;
import me.sshcrack.mc_talking.McTalking;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages background music for citizens during conversations.
 * 
 * Responsibilities:
 * - Select music via LLM queries (tool calls from Gemini Live)
 * - Coordinate yt-dlp streaming and caching
 * - Stream audio chunks to players via voice chat (no full download required)
 * - Track playback state per entity
 * - Handle auto-ducking and track skipping
 * 
 * Architecture:
 * - Uses yt-dlp for on-demand streaming (chunked playback)
 * - Caches downloaded chunks for repeat plays (LRU eviction)
 * - Pipes audio directly from yt-dlp process to voice chat for low latency
 * - Never downloads full video; streams only the needed audio
 */
public class MusicManager {
    private static MusicManager INSTANCE;

    // Track active music sessions: entityId -> session state
    private final Map<UUID, MusicSession> activeSessions = new ConcurrentHashMap<>();
    
    // LRU cache for downloaded music files
    private final Map<String, CacheEntry> musicCache;
    
    // Global cache: context -> YouTube query mapping
    // This allows reusing successful music selections across similar contexts
    private final Map<String, String> contextToQueryCache = new ConcurrentHashMap<>();
    
    private final Path cacheDirectory;
    private final int cacheSizeMB;
    private long currentCacheUsageMB = 0;
    
    private MusicManager(Path cacheDirectory, int cacheSizeMB) {
        this.cacheDirectory = cacheDirectory;
        this.cacheSizeMB = cacheSizeMB;
        
        // LinkedHashMap with access-order to support LRU eviction
        this.musicCache = new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                if (currentCacheUsageMB > cacheSizeMB) {
                    CacheEntry entry = (CacheEntry) eldest.getValue();
                    currentCacheUsageMB -= entry.sizeMB;
                    try {
                        Files.deleteIfExists(entry.path);
                        McTalking.LOGGER.debug("Evicted cached music: {} ({} MB)", eldest.getKey(), entry.sizeMB);
                    } catch (Exception e) {
                        McTalking.LOGGER.warn("Failed to delete evicted cache file", e);
                    }
                    return true;
                }
                return false;
            }
        };
        
        McTalking.LOGGER.info("MusicManager initialized with cache directory: {} (size: {}MB)", 
            cacheDirectory, cacheSizeMB);
    }

    /**
     * Initialize the MusicManager as a singleton.
     * Should be called on server startup after config is loaded.
     */
    public static synchronized void initialize(Path configDir, int cacheSizeMB) {
        if (INSTANCE != null) {
            return;
        }
        
        Path musicCacheDir = configDir.resolve("music_cache");
        try {
            Files.createDirectories(musicCacheDir);
            INSTANCE = new MusicManager(musicCacheDir, cacheSizeMB);
        } catch (Exception e) {
            McTalking.LOGGER.error("Failed to initialize MusicManager", e);
        }
    }

    public static synchronized MusicManager getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("MusicManager not initialized");
        }
        return INSTANCE;
    }

    /**
     * Start background music for an entity (citizen).
     * Called when a conversation or mumbling begins.
     * 
     * @param entityId UUID of the citizen
     * @param context Description of current context (job, location, emotional state, etc.)
     */
    public synchronized void startForEntity(UUID entityId, String context) {
        if (activeSessions.containsKey(entityId)) {
            McTalking.LOGGER.debug("Music already playing for entity {}, skipping start", entityId);
            return;
        }

        MusicSession session = new MusicSession(entityId, context);
        activeSessions.put(entityId, session);
        
        McTalking.LOGGER.debug("Started music session for entity {} with context: {}", entityId, context);
    }

    /**
     * Register or refresh a music session for a citizen entity.
     * Checks the context cache and automatically resumes music if a matching context is found.
     */
    public synchronized void startForEntity(AbstractEntityCitizen citizen, String context) {
        UUID entityId = citizen.getUUID();
        
        if (activeSessions.containsKey(entityId)) {
            McTalking.LOGGER.debug("Music already playing for entity {}, skipping start", entityId);
            return;
        }

        // Create structured context
        MusicContext musicContext = MusicContext.fromCitizen(citizen, context);
        String cacheKey = musicContext.toCacheKey();
        
        // Check if we have a cached query for this context
        String cachedQuery = contextToQueryCache.get(cacheKey);
        
        if (cachedQuery != null) {
            McTalking.LOGGER.debug("Found cached music query for context '{}': {}", context, cachedQuery);
            // Automatically play the cached music
            playQueryForEntity(citizen, cachedQuery, context);
        } else {
            // No cached query, create session and wait for AI to select music
            MusicSession session = new MusicSession(entityId, context);
            activeSessions.put(entityId, session);
            McTalking.LOGGER.debug("Started music session for entity {} with context: {} (waiting for AI selection)", entityId, context);
        }
    }

    /**
     * Resolve a search query and stream the first matching track to the citizen's voice channel.
     * Caches the query for this context to enable automatic music resumption.
     */
    public synchronized boolean playQueryForEntity(AbstractEntityCitizen citizen, String query, String triggerSource) {
        if (citizen == null || query == null || query.isBlank()) {
            return false;
        }

        if (McTalkingVoicechatPlugin.vcApi == null) {
            McTalking.LOGGER.warn("Voice chat is unavailable; cannot play background music for {}", citizen.getUUID());
            return false;
        }

        var searchResult = YtDlpRunner.resolveSearchResult(query);
        if (searchResult == null) {
            McTalking.LOGGER.warn("No YouTube results found for music query '{}'", query);
            return false;
        }

        MusicSession session = activeSessions.computeIfAbsent(citizen.getUUID(), id -> new MusicSession(id, triggerSource));
        session.stopPlayback();
        session.context = triggerSource;
        session.currentTrackTitle = searchResult.title();
        session.currentTrackArtist = searchResult.uploader();
        session.currentTrackUrl = searchResult.webpageUrl();
        session.isAgeRestricted = searchResult.ageRestricted();

        // Cache the query for this context
        MusicContext musicContext = MusicContext.fromCitizen(citizen, triggerSource);
        String cacheKey = musicContext.toCacheKey();
        contextToQueryCache.put(cacheKey, query);
        McTalking.LOGGER.debug("Cached music query '{}' for context '{}'", query, cacheKey);

        try {
            AudioChannel channel = McTalkingVoicechatPlugin.vcApi.createEntityAudioChannel(
                    UUID.randomUUID(),
                    McTalkingVoicechatPlugin.vcApi.fromEntity(citizen)
            );
            if (channel == null) {
                McTalking.LOGGER.warn("Failed to create music audio channel for citizen {}", citizen.getUUID());
                return false;
            }
            channel.setCategory(McTalkingVoicechatPlugin.MUSIC);

            GeminiStream stream = new GeminiStream(channel);
            session.attachPlayback(stream);

            YtDlpRunner.PcmAudioPipeline pipeline = YtDlpRunner.streamAudioAsPcm(searchResult.webpageUrl());
            if (pipeline == null) {
                session.stopPlayback();
                return false;
            }

            session.attachPipeline(pipeline);
            Thread playbackThread = new Thread(() -> pumpMusicAudio(session), "mc-music-" + citizen.getUUID());
            playbackThread.setDaemon(true);
            session.attachPlaybackThread(playbackThread);
            session.isStreaming = true;
            playbackThread.start();
            McTalking.LOGGER.info("Playing background music for {} from search query '{}' -> {}", citizen.getUUID(), query, searchResult.webpageUrl());
            return true;
        } catch (Exception e) {
            McTalking.LOGGER.error("Failed to start background music for citizen {}", citizen.getUUID(), e);
            session.stopPlayback();
            return false;
        }
    }

    private void pumpMusicAudio(MusicSession session) {
        YtDlpRunner.PcmAudioPipeline pipeline = session.pipeline;
        GeminiStream stream = session.musicStream;
        if (pipeline == null || stream == null) {
            return;
        }

        byte[] buffer = new byte[4096];
        try (InputStream inputStream = pipeline.inputStream()) {
            int read;
            while (!Thread.currentThread().isInterrupted() && (read = inputStream.read(buffer)) != -1) {
                // Apply volume scaling for auto-ducking
                session.updateVolume();
                float volume = session.getCurrentVolume();
                
                byte[] chunk = Arrays.copyOf(buffer, read);
                
                // Scale PCM samples by volume (assuming 16-bit signed PCM)
                if (volume < 1.0f) {
                    for (int i = 0; i < read - 1; i += 2) {
                        // Read 16-bit sample (little-endian)
                        short sample = (short) ((chunk[i] & 0xFF) | (chunk[i + 1] << 8));
                        // Scale by volume
                        sample = (short) (sample * volume);
                        // Write back
                        chunk[i] = (byte) (sample & 0xFF);
                        chunk[i + 1] = (byte) ((sample >> 8) & 0xFF);
                    }
                }
                
                stream.addGeminiPcmWithPitch(chunk, McTalkingVoicechatPlugin.TARGET_SAMPLE_RATE);
            }
            stream.flushAudio();
        } catch (Exception e) {
            McTalking.LOGGER.debug("Background music stream ended or failed for {}", session.entityId, e);
        } finally {
            session.isStreaming = false;
            session.closePipeline();
            stream.close();
        }
    }

    /**
     * Stop background music for an entity.
     * Called when a conversation or mumbling ends.
     */
    public synchronized void stopForEntity(UUID entityId) {
        MusicSession session = activeSessions.remove(entityId);
        if (session != null) {
            McTalking.LOGGER.debug("Stopped music session for entity {}", entityId);
            session.stop();
        }
    }

    /**
     * Skip to next track for an entity.
     * This will trigger the AI to select a new track via SelectMusicAction.
     */
    public synchronized void skip(UUID entityId) {
        MusicSession session = activeSessions.get(entityId);
        if (session != null) {
            McTalking.LOGGER.debug("Skipped track for entity {}", entityId);
            session.stopPlayback();
            // The AI will call SelectMusicAction again to select a new track
        }
    }

    /**
     * Get current status of music playback for an entity.
     * Returns a JSON object with track metadata.
     */
    @Nullable
    public synchronized JsonObject getStatus(UUID entityId) {
        MusicSession session = activeSessions.get(entityId);
        if (session == null) {
            return null;
        }
        return session.toJson();
    }

    /**
     * Check if yt-dlp is available on the system.
     * Logs a warning if not found.
     * 
     * @return true if yt-dlp is available in PATH
     */
    public static boolean isYtDlpAvailable() {
        return YtDlpRunner.isAvailable();
    }

    /**
     * Duck (reduce) music volume for an entity when speech starts.
     * Applies smooth volume transition.
     */
    public synchronized void duckMusicForEntity(UUID entityId) {
        MusicSession session = activeSessions.get(entityId);
        if (session != null && session.musicStream != null) {
            double attenuation = me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().musicDuckingAttenuation;
            session.setTargetVolume((float) attenuation);
            McTalking.LOGGER.debug("Ducking music for entity {} to volume {}", entityId, attenuation);
        }
    }

    /**
     * Restore music volume for an entity when speech ends.
     * Applies smooth volume transition.
     */
    public synchronized void restoreMusicForEntity(UUID entityId) {
        MusicSession session = activeSessions.get(entityId);
        if (session != null && session.musicStream != null) {
            session.setTargetVolume(1.0f);
            McTalking.LOGGER.debug("Restoring music volume for entity {}", entityId);
        }
    }

    /**
     * Get the cache directory path.
     */
    public Path getCacheDirectory() {
        return cacheDirectory;
    }

    /**
     * Get the configured cache size in MB.
     */
    public int getCacheSizeMB() {
        return cacheSizeMB;
    }

    /**
     * Get current cache usage in MB.
     */
    public long getCurrentCacheUsageMB() {
        return currentCacheUsageMB;
    }

    /**
     * Get number of active music sessions.
     */
    public synchronized int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Cache entry metadata for LRU tracking.
     */
    private static class CacheEntry {
        final Path path;
        final int sizeMB;
        
        CacheEntry(Path path, int sizeMB) {
            this.path = path;
            this.sizeMB = sizeMB;
        }
    }

    /**
     * Internal class representing a single music session for an entity.
     */
    static class MusicSession {
        final UUID entityId;
        String context;
        
        @Nullable String currentTrackTitle;
        @Nullable String currentTrackArtist;
        @Nullable String currentTrackUrl;
        
        boolean isAgeRestricted;
        boolean isStreaming;
        long startTime;
        @Nullable GeminiStream musicStream;
        @Nullable YtDlpRunner.PcmAudioPipeline pipeline;
        @Nullable Thread playbackThread;
        
        // Volume control for auto-ducking
        private volatile float currentVolume = 1.0f;
        private volatile float targetVolume = 1.0f;

        MusicSession(UUID entityId, String context) {
            this.entityId = entityId;
            this.context = context;
            this.startTime = System.currentTimeMillis();
            this.isStreaming = false;
        }

        void stopPlayback() {
            if (playbackThread != null) {
                playbackThread.interrupt();
            }
            if (musicStream != null) {
                musicStream.stop();
                musicStream.close();
            }
            closePipeline();
            playbackThread = null;
            musicStream = null;
            isStreaming = false;
        }

        void attachPlayback(GeminiStream musicStream) {
            this.musicStream = musicStream;
        }

        void attachPipeline(YtDlpRunner.PcmAudioPipeline pipeline) {
            this.pipeline = pipeline;
        }

        void attachPlaybackThread(Thread playbackThread) {
            this.playbackThread = playbackThread;
        }

        void closePipeline() {
            if (pipeline != null) {
                pipeline.close();
                pipeline = null;
            }
        }

        void stop() {
            stopPlayback();
        }
        
        /**
         * Set target volume for smooth crossfade.
         */
        void setTargetVolume(float volume) {
            this.targetVolume = Math.max(0.0f, Math.min(1.0f, volume));
        }
        
        /**
         * Get current volume (smoothly transitions to target).
         */
        float getCurrentVolume() {
            return currentVolume;
        }
        
        /**
         * Update volume with smooth transition.
         * Call this periodically during playback.
         */
        void updateVolume() {
            if (Math.abs(currentVolume - targetVolume) < 0.01f) {
                currentVolume = targetVolume;
            } else {
                // Smooth transition over time (adjust step size for crossfade duration)
                float step = 0.05f; // Adjust based on musicCrossfadeDurationMs
                if (currentVolume < targetVolume) {
                    currentVolume = Math.min(currentVolume + step, targetVolume);
                } else {
                    currentVolume = Math.max(currentVolume - step, targetVolume);
                }
            }
        }

        JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("entity_id", entityId.toString());
            obj.addProperty("context", context);
            obj.addProperty("is_streaming", isStreaming);
            
            if (currentTrackTitle != null) {
                obj.addProperty("title", currentTrackTitle);
                obj.addProperty("artist", currentTrackArtist);
                obj.addProperty("url", currentTrackUrl);
                obj.addProperty("age_restricted", isAgeRestricted);
                obj.addProperty("elapsed_ms", System.currentTimeMillis() - startTime);
            } else {
                obj.addProperty("status", "waiting_for_selection");
            }
            
            return obj;
        }
    }
}
