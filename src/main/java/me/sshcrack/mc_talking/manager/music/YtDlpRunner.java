package me.sshcrack.mc_talking.manager.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.sshcrack.mc_talking.McTalking;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper for yt-dlp process management.
 * Detects yt-dlp availability, streams audio (or chunks from cache), and manages caching.
 * 
 * Streaming Strategy:
 * - Uses yt-dlp with HTTP range requests to fetch audio on-demand (chunked)
 * - Does NOT download the full video before playback
 * - Cache stores downloaded chunks for repeat plays
 * - Streaming command: yt-dlp -f 'bestaudio[ext=m4a]' -o '-' (outputs to stdout for direct streaming)
 */
public class YtDlpRunner {
    private static final String YTDLP_COMMAND = "yt-dlp";
    private static final String FFMPEG_COMMAND = "ffmpeg";
    private static boolean ytdlpAvailable = false;
    private static boolean ytdlpChecked = false;
    private static boolean ffmpegAvailable = false;
    private static boolean ffmpegChecked = false;

    /**
     * Check if yt-dlp is available in the system PATH.
     * Performs detection only once per runtime.
     */
    public static synchronized boolean isAvailable() {
        if (ytdlpChecked) {
            return ytdlpAvailable;
        }

        ytdlpChecked = true;
        
        try {
            ProcessBuilder pb = new ProcessBuilder(YTDLP_COMMAND, "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                ytdlpAvailable = true;
                McTalking.LOGGER.info("yt-dlp detected in system PATH");
                return true;
            }
        } catch (IOException | InterruptedException e) {
            // yt-dlp not found
        }

        ytdlpAvailable = false;
        McTalking.LOGGER.warn("yt-dlp not found in system PATH. Background music feature will be disabled.");
        McTalking.LOGGER.warn("To enable music, install yt-dlp: https://github.com/yt-dlp/yt-dlp");
        return false;
    }

    /**
     * Reset the availability check (useful for testing).
     */
    public static synchronized void resetAvailabilityCheck() {
        ytdlpChecked = false;
        ytdlpAvailable = false;
        ffmpegChecked = false;
        ffmpegAvailable = false;
    }

    /**
     * Check if ffmpeg is available in the system PATH.
     */
    public static synchronized boolean isFfmpegAvailable() {
        if (ffmpegChecked) {
            return ffmpegAvailable;
        }

        ffmpegChecked = true;

        try {
            ProcessBuilder pb = new ProcessBuilder(FFMPEG_COMMAND, "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                ffmpegAvailable = true;
                McTalking.LOGGER.info("ffmpeg detected in system PATH");
                return true;
            }
        } catch (IOException | InterruptedException e) {
            // ffmpeg not found
        }

        ffmpegAvailable = false;
        McTalking.LOGGER.warn("ffmpeg not found in system PATH. Background music playback will be disabled.");
        return false;
    }

    /**
     * Notify admin players that yt-dlp is not detected.
     * Called when a player joins the server or enables music without yt-dlp available.
     */
    public static void notifyAdminPlayersOfMissingYtDlp(ServerPlayer player) {
        if (!isAvailable() && player.hasPermissions(4)) {  // 4 = OP level
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                    "§c[McTalking] yt-dlp not detected. Background music feature is DISABLED. " +
                    "Install yt-dlp to enable: https://github.com/yt-dlp/yt-dlp"
                ),
                false  // Chat message (not action bar)
            );
        }
    }

    /**
     * Stream audio from YouTube with optional caching.
     * Spawns yt-dlp process and returns an InputStream for immediate playback.
     * Audio is piped from yt-dlp stdout directly to voice chat.
     * 
     * @param youtubeUrl YouTube video URL
     * @param cachePath Optional path to cache the audio. Can be null to skip caching.
     * @return InputStream of audio data, or null if unavailable/error
     */
    public static java.io.InputStream streamAudioWithCache(String youtubeUrl, java.nio.file.Path cachePath) {
        if (!isAvailable()) {
            McTalking.LOGGER.error("yt-dlp not available, cannot stream audio from: {}", youtubeUrl);
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                YTDLP_COMMAND,
                "-f", "bestaudio[ext=m4a]",  // Best audio format for streaming
                "-o", "-",                   // Output to stdout for direct streaming
                "--no-part",                 // Don't create .part files
                "--no-warnings",             // Suppress warnings to keep output clean
                youtubeUrl
            );
            
            // Redirect stderr to avoid polluting the audio stream
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            
            Process process = pb.start();
            java.io.InputStream audioStream = process.getInputStream();
            
            // Optional: Wrap with caching stream that writes to cachePath while reading
            if (cachePath != null) {
                try {
                    java.nio.file.Files.createDirectories(cachePath.getParent());
                    java.io.OutputStream cacheOut = java.nio.file.Files.newOutputStream(cachePath);
                    audioStream = new TeeInputStream(audioStream, cacheOut, true);
                } catch (Exception e) {
                    McTalking.LOGGER.warn("Failed to create cache file, streaming without caching: {}", e.getMessage());
                    // Continue with uncached stream
                }
            }
            
            return audioStream;
        } catch (Exception e) {
            McTalking.LOGGER.error("Failed to start yt-dlp stream process", e);
            return null;
        }
    }

    /**
     * Resolve the first yt-dlp search result for a freeform query.
     */
    @Nullable
    public static SearchResult resolveSearchResult(String query) {
        if (!isAvailable()) {
            return null;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    YTDLP_COMMAND,
                    "--dump-single-json",
                    "--skip-download",
                    "--no-playlist",
                    "--no-warnings",
                    "ytsearch1:" + query
            );
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isBlank()) {
                McTalking.LOGGER.warn("yt-dlp search failed for query: {} (exit code {})", query, exitCode);
                return null;
            }

            JsonObject json = JsonParser.parseString(output).getAsJsonObject();
            JsonObject entry = json;
            if (json.has("entries")) {
                JsonArray entries = json.getAsJsonArray("entries");
                if (!entries.isEmpty() && entries.get(0).isJsonObject()) {
                    entry = entries.get(0).getAsJsonObject();
                }
            }

            String webpageUrl = entry.has("webpage_url") ? entry.get("webpage_url").getAsString() : null;
            if (webpageUrl == null || webpageUrl.isBlank()) {
                return null;
            }

            return new SearchResult(
                    webpageUrl,
                    entry.has("title") ? entry.get("title").getAsString() : query,
                    entry.has("uploader") ? entry.get("uploader").getAsString() : "Unknown",
                    entry.has("description") ? entry.get("description").getAsString() : "",
                    entry.has("age_limit") && entry.get("age_limit").getAsInt() >= 18
            );
        } catch (Exception e) {
            McTalking.LOGGER.error("Failed to resolve YouTube search result for query: {}", query, e);
            return null;
        }
    }

    /**
     * Start a yt-dlp + ffmpeg pipeline that outputs raw 48kHz mono PCM audio.
     */
    @Nullable
    public static PcmAudioPipeline streamAudioAsPcm(String youtubeUrl) {
        if (!isAvailable() || !isFfmpegAvailable()) {
            return null;
        }

        try {
            ProcessBuilder ytDlp = new ProcessBuilder(
                    YTDLP_COMMAND,
                    "-f", "bestaudio[ext=m4a]/bestaudio/best",
                    "-o", "-",
                    "--no-part",
                    "--no-playlist",
                    "--no-warnings",
                    youtubeUrl
            );
            ytDlp.redirectError(ProcessBuilder.Redirect.INHERIT);

            ProcessBuilder ffmpeg = new ProcessBuilder(
                    FFMPEG_COMMAND,
                    "-hide_banner",
                    "-loglevel", "error",
                    "-i", "pipe:0",
                    "-f", "s16le",
                    "-acodec", "pcm_s16le",
                    "-ac", "1",
                    "-ar", String.valueOf(me.sshcrack.mc_talking.McTalkingVoicechatPlugin.TARGET_SAMPLE_RATE),
                    "pipe:1"
            );
            ffmpeg.redirectError(ProcessBuilder.Redirect.INHERIT);

            List<Process> processes = ProcessBuilder.startPipeline(List.of(ytDlp, ffmpeg));
            if (processes.size() < 2) {
                return null;
            }

            return new PcmAudioPipeline(processes.get(0), processes.get(1), processes.get(1).getInputStream());
        } catch (Exception e) {
            McTalking.LOGGER.error("Failed to start yt-dlp/ffmpeg audio pipeline for: {}", youtubeUrl, e);
            return null;
        }
    }

    /**
     * Download audio stream from YouTube (full file, for caching after streaming).
     * Spawns yt-dlp process and waits for completion.
     * 
     * @param youtubeUrl YouTube video URL
     * @param outputPath Path where to save the audio file
     * @return true if successful, false otherwise
     */
    public static boolean downloadAudioStream(String youtubeUrl, java.nio.file.Path outputPath) {
        if (!isAvailable()) {
            McTalking.LOGGER.error("yt-dlp not available, cannot download audio from: {}", youtubeUrl);
            return false;
        }

        try {
            java.nio.file.Files.createDirectories(outputPath.getParent());
            
            ProcessBuilder pb = new ProcessBuilder(
                YTDLP_COMMAND,
                "-f", "bestaudio[ext=m4a]",  // Best audio in m4a format
                "-o", outputPath.toString(),
                "--progress",                // Show download progress
                "--no-warnings",
                youtubeUrl
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && java.nio.file.Files.exists(outputPath)) {
                McTalking.LOGGER.info("Successfully downloaded audio to: {}", outputPath);
                return true;
            } else {
                McTalking.LOGGER.warn("yt-dlp process failed with exit code: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            McTalking.LOGGER.error("Failed to download audio from YouTube", e);
            return false;
        }
    }

    /**
     * Result of a yt-dlp search query.
     */
    public record SearchResult(String webpageUrl, String title, String uploader, String description,
                               boolean ageRestricted) {
    }

    /**
     * Piped audio stream from yt-dlp through ffmpeg.
     */
    public static class PcmAudioPipeline implements AutoCloseable {
        private final Process ytDlpProcess;
        private final Process ffmpegProcess;
        private final InputStream inputStream;

        PcmAudioPipeline(Process ytDlpProcess, Process ffmpegProcess, InputStream inputStream) {
            this.ytDlpProcess = ytDlpProcess;
            this.ffmpegProcess = ffmpegProcess;
            this.inputStream = inputStream;
        }

        public InputStream inputStream() {
            return inputStream;
        }

        @Override
        public void close() {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
            ytDlpProcess.destroyForcibly();
            ffmpegProcess.destroyForcibly();
        }
    }

    /**
     * Simple TeeInputStream that copies data to an output stream while reading.
     * Used for simultaneous streaming and caching.
     */
    private static class TeeInputStream extends java.io.FilterInputStream {
        private final java.io.OutputStream out;
        private final boolean closeOut;

        TeeInputStream(java.io.InputStream in, java.io.OutputStream out, boolean closeOut) {
            super(in);
            this.out = out;
            this.closeOut = closeOut;
        }

        @Override
        public int read() throws java.io.IOException {
            int b = super.read();
            if (b >= 0) {
                out.write(b);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                out.write(b, off, n);
            }
            return n;
        }

        @Override
        public void close() throws java.io.IOException {
            try {
                super.close();
            } finally {
                if (closeOut) {
                    out.close();
                }
            }
        }
    }

    /**
     * Get metadata for a YouTube URL without downloading.
     * Useful for checking age restrictions, availability, etc.
     * 
     * In prototype, returns mock metadata.
     */
    public static YtDlpMetadata getMetadata(String youtubeUrl) {
        if (!isAvailable()) {
            return null;
        }

        // Mock metadata for prototype
        McTalking.LOGGER.debug("[MOCK] Fetching metadata for: {}", youtubeUrl);
        
        return new YtDlpMetadata(
            "Sample Track Title",      // title
            "Sample Artist",           // uploader
            "Sample Description",      // description
            false                      // age_restricted
        );
    }

    /**
     * Container for yt-dlp metadata about a video.
     */
    public static class YtDlpMetadata {
        public final String title;
        public final String uploader;
        public final String description;
        public final boolean ageRestricted;

        public YtDlpMetadata(String title, String uploader, String description, boolean ageRestricted) {
            this.title = title;
            this.uploader = uploader;
            this.description = description;
            this.ageRestricted = ageRestricted;
        }
    }
}
