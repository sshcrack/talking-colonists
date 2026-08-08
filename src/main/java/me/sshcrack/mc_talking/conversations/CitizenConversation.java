package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.conversations.memory.CitizenMemoryGenerator;
import me.sshcrack.mc_talking.config.ConversationMode;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.manager.GeminiStream;
import me.sshcrack.mc_talking.manager.audio.CitizenEntityAudioProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.TtsQuotaManager;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;

/**
 * Orchestrates a citizen-to-citizen conversation.
 *
 * <p>Depending on {@link ConversationMode} (read from config at construction time):</p>
 * <ul>
 *   <li>{@link ConversationMode#FLASH_TTS} – generates a script with Flash and renders it
 *       through the multi-speaker TTS API (original pipeline).</li>
 *   <li>{@link ConversationMode#LIVE_WEBSOCKETS} – opens one {@link LiveConversationWsClient}
 *       per citizen; the clients feed their audio output to each other in real time.</li>
 * </ul>
 */
public class CitizenConversation {
    private final List<AbstractEntityCitizen> participants;
    private final AtomicReference<ConversationState> state = new AtomicReference<>(ConversationState.GENERATING);
    private final MinecraftServer server;
    private final ConversationMode mode;

    /**
     * Only used in FLASH_TTS mode.
     */
    private GeminiStream stream;

    /**
     * Only used in LIVE_WEBSOCKETS mode.
     */
    private List<LiveConversationWsClient> liveClients;

    private Consumer<ConversationState> onStateChanged;

    /**
     * Set to {@code true} when a player takes over one of the participants via
     * {@link me.sshcrack.mc_talking.item.CitizenTalkingDevice}.
     * The {@code finally} block in {@link #performFlashTtsConversation(Runnable)}
     * checks this flag to skip lifecycle cleanup that would interfere with the
     * player conversation.
     */
    private volatile boolean aborted;

    public enum ConversationState {
        GENERATING,
        PLAYING_AUDIO,
        ENDED
    }

    public CitizenConversation(MinecraftServer server, List<AbstractEntityCitizen> participants) {
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Conversation must have at least one participant");
        }

        this.participants = participants;
        this.server = server;
        this.mode = McTalkingConfig.INSTANCE.instance().conversationMode;

        McTalking.LOGGER.info("Starting conversation [{}] for participants: {}", mode,
                participants.stream().map(c -> c.getName().getString()).toList());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Kicks off the conversation asynchronously.
     * The conversation state will be updated via the {@link #setOnStateChanged} callback.
     */
    public void performConversation() {
        switch (mode) {
            case AUTO -> performAutoConversation();
            case FLASH_TTS -> performFlashTtsConversation();
            case LIVE_WEBSOCKETS -> performLiveWebsocketConversation();
        }
    }

    public void setOnStateChanged(Consumer<ConversationState> callback) {
        this.onStateChanged = callback;
    }

    /**
     * Called by {@link me.sshcrack.mc_talking.ConversationManager} when a player
     * starts a conversation with a participant of this Flash/TTS conversation.
     * Prevents the background thread from setting {@link ConversationState#ENDED},
     * which would interfere with the player conversation's AiStatus.
     */
    public void abort() {
        this.aborted = true;
        if (stream != null) {
            stream.stop();
            stream.close();
        }
    }

    // -------------------------------------------------------------------------
    // Flash + TTS mode (original pipeline)
    // -------------------------------------------------------------------------

    private void performFlashTtsConversation() {
        performFlashTtsConversation(null);
    }

    private void performFlashTtsConversation(Runnable fallback) {
        // Guard: all participants must be able to speak
        for (AbstractEntityCitizen p : participants) {
            if (!ConversationManager.canCitizenSpeak(p)) {
                setState(ConversationState.ENDED);
                return;
            }
        }

        if (stream == null) {
            var channel = constructLocationalAudioChannel();
            if (channel == null) {
                McTalking.LOGGER.error("[Flash/TTS] Failed to create locational audio channel — aborting conversation");
                setState(ConversationState.ENDED);
                return;
            }
            stream = new GeminiStream(channel);
        }

        // Mark participants as busy so they can't be double-booked
        for (AbstractEntityCitizen p : participants) {
            ConversationManager.markBusy(p);
            ConversationManager.registerAbortHandler(p, this::abort);
        }

        new Thread(() -> {
            setState(ConversationState.GENERATING);
            boolean fallbackTriggered = false;

            try {
                AtomicBoolean playbackStarted = new AtomicBoolean(false);
                String completedTranscript = CitizenConversationGenerator.generateConversation(
                        participants, server,
                        chunk -> {
                            if (playbackStarted.compareAndSet(false, true)) {
                                setState(ConversationState.PLAYING_AUDIO);
                            }
                            stream.addGeminiPcmWithPitch(chunk.audioBytes(), chunk.sampleRate());
                        });

                // The websocket client flushes its stream when generation completes,
                // but the Flash/TTS path is synchronous and has to do it explicitly.
                // Without this, a short final chunk can remain buffered forever.
                stream.flushAudio();

                long playbackDeadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(2);
                while (!aborted && stream.hasPendingPlayback() && System.nanoTime() < playbackDeadline) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new ConversationGenerationException("Conversation playback was interrupted", interrupted);
                    }
                }

                boolean playbackCompleted = !stream.hasPendingPlayback();
                if (!aborted && !playbackCompleted) {
                    McTalking.LOGGER.warn("[Flash/TTS] Playback did not drain before timeout; stopping stream");
                    stream.stop();
                }

                // Only spend the optional extra Flash request on memories after the
                // generated conversation was actually heard to completion. Aborted or
                // timed-out playback must not create memories for unheard dialogue.
                if (!aborted && playbackCompleted && playbackStarted.get()
                        && McTalkingConfig.INSTANCE.instance().enableConversationSummaryAndMemorize) {
                    CitizenMemoryGenerator.addAndGenerateMemory(completedTranscript, participants, server)
                            .scheduleOrSaveMemory();
                }

            } catch (ConversationGenerationException e) {
                McTalking.LOGGER.error("Failed to generate Flash/TTS conversation: {}, cause: {}",
                        e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "none");
                if (fallback != null) {
                    fallbackTriggered = true;
                    McTalking.LOGGER.info("[Auto] Flash/TTS failed, falling back to Live WebSockets");
                    if (stream != null) {
                        stream.stop();
                        stream.close();
                    }
                    fallback.run();
                }
            } finally {
                for (AbstractEntityCitizen p : participants) {
                    ConversationManager.unregisterAbortHandler(p);
                    ConversationManager.markNotBusy(p);
                    if (!fallbackTriggered && !aborted) {
                        ConversationManager.recordCooldown(p);
                        AiStatusHelper.setAiStatusSynced(p, AiStatus.NONE);
                    }
                }

                if (!fallbackTriggered && !aborted) {
                    setState(ConversationState.ENDED);
                }
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Live WebSocket mode (cheaper)
    // -------------------------------------------------------------------------

    private void performLiveWebsocketConversation() {
        if (participants.size() < 2) {
            McTalking.LOGGER.warn("[LiveConv] Need at least 2 participants, got {}. Aborting.", participants.size());
            setState(ConversationState.ENDED);
            return;
        }

        AbstractEntityCitizen citizenA = participants.get(0);
        AbstractEntityCitizen citizenB = participants.get(1);

        // "Already busy" guard: abort if either citizen is already in any session
        if (!ConversationManager.canCitizenSpeak(citizenA) || !ConversationManager.canCitizenSpeak(citizenB)) {
            McTalking.LOGGER.info("[LiveConv] One or both citizens can't speak, aborting");
            setState(ConversationState.ENDED);
            return;
        }

        // Capacity check: we need 2 low-priority slots
        if (!ConversationManager.hasLowPriorityCapacity(2)) {
            McTalking.LOGGER.info("[LiveConv] Not enough free slots for citizen-to-citizen conversation, aborting");
            setState(ConversationState.ENDED);
            return;
        }

        // Claim both slots (low-priority) before creating any clients
        if (!ConversationManager.claimSlot(citizenA, false) || !ConversationManager.claimSlot(citizenB, false)) {
            // Shouldn't happen after the capacity check above, but be safe
            ConversationManager.releaseSlot(citizenA);
            ConversationManager.releaseSlot(citizenB);
            McTalking.LOGGER.warn("[LiveConv] Failed to claim slots, aborting");
            setState(ConversationState.ENDED);
            return;
        }

        setState(ConversationState.GENERATING);

        AtomicInteger sharedTurnCounter = new AtomicInteger(0);
        AtomicBoolean cleanupStarted = new AtomicBoolean(false);
        AtomicReference<LiveConversationWsClient> clientARef = new AtomicReference<>();
        AtomicReference<LiveConversationWsClient> clientBRef = new AtomicReference<>();

        Consumer<LiveConversationWsClient> onClientEnded = client -> {
            if (!cleanupStarted.compareAndSet(false, true)) {
                return;
            }

            // WebSocket callbacks do not necessarily run on the Minecraft server
            // thread. End the pair together and only unregister the exact clients
            // created by this conversation; a player takeover may already have
            // replaced one of them in ConversationManager.
            server.execute(() -> {
                LiveConversationWsClient expectedA = clientARef.get();
                LiveConversationWsClient expectedB = clientBRef.get();

                boolean removedA = expectedA != null
                        && ConversationManager.unregisterExternalClient(citizenA, expectedA);
                boolean removedB = expectedB != null
                        && ConversationManager.unregisterExternalClient(citizenB, expectedB);

                if (removedA) {
                    if (!expectedA.isClosed()) expectedA.close();
                    ConversationManager.recordCooldown(citizenA);
                }
                if (removedB) {
                    if (!expectedB.isClosed()) expectedB.close();
                    ConversationManager.recordCooldown(citizenB);
                }

                setState(ConversationState.ENDED);
            });
        };

        var citizenDataA = citizenA.getCitizenData();
        var viewA = CitizenPromptViewFactory.create(citizenDataA, new HashMap<>(), null);

        var basicPromptB = """
                You are about to start a conversation with a fellow citizen %s.
                This is some basic information about them. Talk naturally and according to your feelings.
                %s
                """.formatted(citizenDataA.getName(), CitizenPromptService.getBasicCitizenInfoPrompt(viewA));

        LiveConversationWsClient clientA;
        LiveConversationWsClient clientB;
        try {
            clientA = new LiveConversationWsClient(
                    new CitizenEntityAudioProvider(citizenA, McTalkingVoicechatPlugin.CITIZEN_CONVERSATION),
                    citizenA, sharedTurnCounter, onClientEnded);
            clientARef.set(clientA);

            clientB = new LiveConversationWsClient(
                    new CitizenEntityAudioProvider(citizenB, McTalkingVoicechatPlugin.CITIZEN_CONVERSATION),
                    citizenB, sharedTurnCounter, onClientEnded, basicPromptB);
            clientBRef.set(clientB);
        } catch (RuntimeException e) {
            // Client construction happens after both foreground slots are claimed.
            // Release them explicitly so a failed audio/client constructor cannot
            // permanently consume free-tier capacity. Suppress close callbacks here
            // because the clients have not been registered in ConversationManager.
            cleanupStarted.set(true);
            LiveConversationWsClient partialA = clientARef.get();
            if (partialA != null) {
                try { partialA.close(); } catch (Exception ignored) { }
            }
            ConversationManager.releaseSlot(citizenA);
            ConversationManager.releaseSlot(citizenB);
            McTalking.LOGGER.error("[LiveConv] Failed to construct paired Gemini clients", e);
            setState(ConversationState.ENDED);
            return;
        }

        clientA.setPeer(clientB);
        clientB.setPeer(clientA);

        liveClients = List.of(clientA, clientB);

        // Register in ConversationManager so "already busy" and slot queries work
        ConversationManager.registerExternalClient(citizenA, clientA);
        ConversationManager.registerExternalClient(citizenB, clientB);

        try {
            clientA.connect();
            clientB.connect();
        } catch (RuntimeException e) {
            McTalking.LOGGER.error("[LiveConv] Failed to connect paired Gemini sessions", e);
            onClientEnded.accept(clientA);
            return;
        }

        // Kick off the dialogue from A's side

        var citizenDataB = citizenB.getCitizenData();
        var view = CitizenPromptViewFactory.create(citizenDataB, new HashMap<>(), null);
        clientA.addPromptTextAfterTalkingComplete(
                "Start the conversation! You are talking to a fellow " + citizenDataB.getName() + " basic information about them:" + CitizenPromptService.getBasicCitizenInfoPrompt(view));

        setState(ConversationState.PLAYING_AUDIO);
    }

    // -------------------------------------------------------------------------
    // Auto mode (try Flash+TTS, fall back to Live WebSockets)
    // -------------------------------------------------------------------------

    private void performAutoConversation() {
        if (TtsQuotaManager.isTtsFailed()) {
            McTalking.LOGGER.info("[Auto] TTS previously failed, skipping directly to Live WebSockets");
            performLiveWebsocketConversation();
            return;
        }

        performFlashTtsConversation(() -> server.execute(() -> {
            McTalking.LOGGER.info("[Auto] Running fallback Live WebSockets conversation");
            performLiveWebsocketConversation();
        }));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LocationalAudioChannel constructLocationalAudioChannel() {
        Vec3 sum = Vec3.ZERO;
        Vec3 minPos = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 maxPos = new Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);

        for (AbstractEntityCitizen citizen : participants) {
            double x = citizen.getX();
            double y = citizen.getY();
            double z = citizen.getZ();
            sum = sum.add(x, y, z);
            maxPos = new Vec3(Math.max(maxPos.x, x), Math.max(maxPos.y, y), Math.max(maxPos.z, z));
            minPos = new Vec3(Math.min(minPos.x, x), Math.min(minPos.y, y), Math.min(minPos.z, z));
        }

        Vec3 avg = sum.scale(1.0d / participants.size());
        //noinspection SequencedCollectionMethodCanBeUsed
        ServerLevel level = (ServerLevel) participants.get(0).level();

        UUID channelId = UUID.randomUUID();
        var vcLevel = vcApi.fromServerLevel(level);
        var avgPos = vcApi.createPosition(avg.x, avg.y, avg.z);

        var channel = vcApi.createLocationalAudioChannel(channelId, vcLevel, avgPos);
        if (channel == null) {
            McTalking.LOGGER.error("Failed to create locational audio channel for conversation!");
            return null;
        }

        float maxDistance = (float) maxPos.subtract(minPos).length();
        channel.setDistance(Math.max(maxDistance, channel.getDistance()));
        channel.setCategory(McTalkingVoicechatPlugin.CITIZEN_CONVERSATION);
        return channel;
    }

    private void setState(ConversationState newState) {
        McTalking.LOGGER.info("Conversation state changed to {}", newState);
        state.set(newState);
        if (onStateChanged != null) {
            onStateChanged.accept(newState);
        }
    }
}
