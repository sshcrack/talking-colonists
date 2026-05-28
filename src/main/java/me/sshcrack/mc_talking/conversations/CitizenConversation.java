package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.config.ConversationMode;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.manager.GeminiStream;
import me.sshcrack.mc_talking.manager.audio.CitzienEntityAudioProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

import me.sshcrack.mc_talking.config.McTalkingConfig;

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
        if (mode == ConversationMode.FLASH_TTS) {
            performFlashTtsConversation();
        } else {
            performLiveWebsocketConversation();
        }
    }

    public void setOnStateChanged(Consumer<ConversationState> callback) {
        this.onStateChanged = callback;
    }

    // -------------------------------------------------------------------------
    // Flash + TTS mode (original pipeline)
    // -------------------------------------------------------------------------

    private void performFlashTtsConversation() {
        if (stream == null) {
            stream = new GeminiStream(constructLocationalAudioChannel());
        }

        new Thread(() -> {
            setState(ConversationState.GENERATING);
            try {
                CitizenConversationGenerator.generateConversation(
                        participants, server,
                        chunk -> stream.addGeminiPcmWithPitch(chunk.audioBytes(), chunk.sampleRate()));
            } catch (ConversationGenerationException e) {
                McTalking.LOGGER.error("Failed to generate Flash/TTS conversation: {}, cause: {}",
                        e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "none");
            } finally {
                setState(ConversationState.ENDED);
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
        UUID idA = citizenA.getUUID();
        UUID idB = citizenB.getUUID();

        // "Already busy" guard: abort if either citizen is already in any session
        if (ConversationManager.canCitizenSpeak(citizenA) || ConversationManager.canCitizenSpeak(citizenB)) {
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
        if (!ConversationManager.claimSlot(idA, false) || !ConversationManager.claimSlot(idB, false)) {
            // Shouldn't happen after the capacity check above, but be safe
            ConversationManager.releaseSlot(idA);
            ConversationManager.releaseSlot(idB);
            McTalking.LOGGER.warn("[LiveConv] Failed to claim slots, aborting");
            setState(ConversationState.ENDED);
            return;
        }

        setState(ConversationState.GENERATING);

        AtomicInteger sharedTurnCounter = new AtomicInteger(0);
        AtomicInteger endedCount = new AtomicInteger(0);

        Consumer<LiveConversationWsClient> onClientEnded = client -> {
            int ended = endedCount.incrementAndGet();
            if (ended >= 2) {
                // Close any still-open peer, unregister both slots
                if (liveClients != null) {
                    liveClients.forEach(c -> {
                        if (!c.isClosed()) c.close();
                    });
                }
                ConversationManager.unregisterExternalClient(citizenA);
                ConversationManager.unregisterExternalClient(citizenB);
                // Record per-citizen cooldowns so they aren't immediately re-selected
                ConversationManager.recordCooldown(citizenA);
                ConversationManager.recordCooldown(citizenA);
                setState(ConversationState.ENDED);
            }
        };

        var citizenDataA = citizenA.getCitizenData();
        var viewA = CitizenPromptViewFactory.create(citizenDataA, new HashMap<>(), null);

        var basicPromptB = """
                You are about to start a conversation with a fellow citizen %s.
                This is some basic information about them. Talk naturally and according to your feelings.
                %s
                """.formatted(citizenDataA.getName(), CitizenPromptService.getBasicCitizenInfoPrompt(viewA));

        LiveConversationWsClient clientA = new LiveConversationWsClient(
                new CitzienEntityAudioProvider(citizenA, McTalkingVoicechatPlugin.CITIZEN_CONVERSATION),
                citizenA, sharedTurnCounter, onClientEnded);

        LiveConversationWsClient clientB = new LiveConversationWsClient(
                new CitzienEntityAudioProvider(citizenB, McTalkingVoicechatPlugin.CITIZEN_CONVERSATION),
                citizenB, sharedTurnCounter, onClientEnded, basicPromptB);

        clientA.setPeer(clientB);
        clientB.setPeer(clientA);

        liveClients = List.of(clientA, clientB);

        // Register in ConversationManager so "already busy" and slot queries work
        ConversationManager.registerExternalClient(citizenA, clientA);
        ConversationManager.registerExternalClient(citizenB, clientB);

        clientA.connect();
        clientB.connect();

        // Kick off the dialogue from A's side

        var citizenDataB = citizenB.getCitizenData();
        var view = CitizenPromptViewFactory.create(citizenDataB, new HashMap<>(), null);
        clientA.addPromptTextAfterTalkingComplete(
                "Start the conversation! You are talking to a fellow " + citizenDataB.getName() + " basic information about them:" + CitizenPromptService.getBasicCitizenInfoPrompt(view));

        setState(ConversationState.PLAYING_AUDIO);
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
