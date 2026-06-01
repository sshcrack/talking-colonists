package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A {@link GeminiWsClient} that represents one participant in a Live-WebSocket
 * citizen-to-citizen conversation.
 *
 * <p>After the AI finishes speaking, the raw PCM audio is forwarded directly to
 * {@link #peer}'s {@link #addPromptAudio} so that the two instances keep
 * conversing without any extra API calls.</p>
 *
 * <p>A round counter limits the total number of speaking turns so the
 * conversation eventually ends.</p>
 */
public class LiveConversationWsClient extends GeminiWsClient {

    /**
     * Maximum number of speaking turns (across both participants) before the session closes.
     */
    private static final int MAX_TOTAL_TURNS = 10;
    @Nullable
    private final String systemPromptAddition;

    /**
     * Peer client that will receive our generated audio as its input.
     */
    @Nullable
    private LiveConversationWsClient peer;

    /**
     * Shared turn counter so both clients can agree when to stop.
     */
    private final AtomicInteger sharedTurnCounter;

    /**
     * Callback invoked when this client's session ends (either naturally or on error).
     */
    private final Consumer<LiveConversationWsClient> onEnded;

    /**
     * The citizen entity this client represents.
     */
    private final AbstractEntityCitizen citizen;

    private volatile boolean holdAudio = false;
    private final List<AudioChunk> heldAudioChunks = new ArrayList<>();

    private record AudioChunk(byte[] data, int sampleRate) {
    }

    // -------------------------------------------------------------------------

    public LiveConversationWsClient(
            AudioProvider audioProvider,
            AbstractEntityCitizen citizen,
            AtomicInteger sharedTurnCounter,
            Consumer<LiveConversationWsClient> onEnded) {
        this(audioProvider, citizen, sharedTurnCounter, onEnded, null);
    }

    public LiveConversationWsClient(
            AudioProvider audioProvider,
            AbstractEntityCitizen citizen,
            AtomicInteger sharedTurnCounter,
            Consumer<LiveConversationWsClient> onEnded,
            @Nullable String systemPromptAddition) {
        super(audioProvider, citizen);
        this.citizen = citizen;
        this.sharedTurnCounter = sharedTurnCounter;
        this.onEnded = onEnded;
        this.systemPromptAddition = systemPromptAddition;
    }

    // -------------------------------------------------------------------------
    // Peer wiring
    // -------------------------------------------------------------------------

    /**
     * Sets the other participant who will receive this client's audio output.
     */
    public void setPeer(@Nullable LiveConversationWsClient peer) {
        this.peer = peer;
    }

    // -------------------------------------------------------------------------
    // GeminiWsClient contract
    // -------------------------------------------------------------------------

    /**
     * Citizen-to-citizen live conversations always use TEXT_AND_AUDIO so we can
     * forward the text transcript to the peer before audio finishes playing,
     * reducing the silence gap between turns.
     */
    @Override
    protected ModalityModes getEffectiveModality() {
        return ModalityModes.TEXT_AND_AUDIO;
    }

    @Override
    protected String getSystemPrompt() {
        Map<UUID, String> others = new HashMap<>();
        if (peer != null) {
            others.put(peer.citizen.getUUID(), peer.citizen.getCitizenData().getName());
        }
        var view = CitizenPromptViewFactory.create(citizen.getCitizenData(), others, null);
        var prompt = CitizenPromptService.generateSystemControlledRoleplayPrompt(view);
        if (systemPromptAddition != null) {
            prompt += "\n\n" + systemPromptAddition;
        }

        return prompt;
    }


    @Override
    public void endConversationWhenPossible() {
        if (this.shouldEndConversation) return;
        super.endConversationWhenPossible();
        if (peer != null) {
            peer.endConversationWhenPossible();
        }
    }

    @Nullable
    public LiveConversationWsClient getPeer() {
        return peer;
    }

    @Override
    @Nullable
    protected ServerPlayer resolveActivePlayer() {
        // No player involved in a citizen-to-citizen live conversation.
        return null;
    }

    @Override
    protected void onQuotaExceededEvent(String message) {
        McTalking.LOGGER.warn("[LiveConvWs] Quota exceeded for {}: {}", citizen.getCitizenData().getName(), message);
        AiStatusHelper.setAiStatusOnServerThread(citizen, AiStatus.QUOTA_EXCEEDED);
        onEnded.accept(this);
    }

    @Override
    protected void onErrorEvent(Exception ex) {
        McTalking.LOGGER.error("[LiveConvWs] Error for {}", citizen.getCitizenData().getName(), ex);
        AiStatusHelper.setAiStatusOnServerThread(citizen, AiStatus.ERROR);
        onEnded.accept(this);
    }

    /**
     * Called when text + audio generation for this turn is fully complete (audio
     * may still be playing back locally).
     *
     * <p>We immediately:
     * <ol>
     *   <li>Put the peer into audio-hold mode so its generated audio is buffered
     *       rather than played right away.</li>
     *   <li>Send our transcript directly to the peer's Gemini session so it
     *       starts generating its response without waiting for our audio to finish.</li>
     * </ol>
     * The peer's audio will only be released (and heard) once our own audio has
     * fully played back, via {@link #onConversationEnded()}.
     */
    @Override
    protected void onTranscriptComplete(String transcript) {
        if (peer == null || peer.isClosed()) return;
        McTalking.LOGGER.info("[LiveConvWs] Turn complete - holding peer audio and forwarding transcript to {} ({})",
                peer.getEntity().getCitizenData().getName(), transcript);
        // Hold peer's audio so it doesn't play while we're still speaking.
        peer.holdAudio();
        // Send text immediately so peer starts generating right now.
        peer.addPromptTextImmediate(transcript);
    }

    @Override
    public void onTurnComplete() {
        super.onTurnComplete();

        if (shouldEndConversation) {
            McTalking.LOGGER.info("[LiveConvWs] Ending conversation as requested by {}", citizen.getCitizenData().getName());
            onEnded.accept(this);
        }
    }

    /**
     * Called once our audio has fully played back (stream drained).
     *
     * <p>The peer has been generating since our {@link #onTurnComplete()} fired and
     * its audio has been held in a buffer. We now release that hold so the peer's
     * audio starts playing immediately (or as soon as it finishes generating, if
     * generation is not yet done).
     */
    @Override
    protected void onConversationEnded() {
        super.onConversationEnded(); // sets our own status to LISTENING

        int turn = sharedTurnCounter.incrementAndGet();
        McTalking.LOGGER.info("[LiveConvWs] Turn {} of {} completed by {}",
                turn, MAX_TOTAL_TURNS, citizen.getCitizenData().getName());

        if (turn >= MAX_TOTAL_TURNS) {
            McTalking.LOGGER.info("[LiveConvWs] Max turns reached – ending conversation");
            onEnded.accept(this);
            return;
        }

        // Release the peer's held audio now that we've finished speaking.
        // If peer generation is already complete the buffered audio plays immediately;
        // if still generating it plays as soon as generation finishes.
        if (peer != null && !peer.isClosed()) {
            McTalking.LOGGER.info("[LiveConvWs] Releasing held audio for peer {}",
                    peer.getEntity().getCitizenData().getName());
            peer.releaseHeldAudio();
        }
    }

    @Override
    public boolean shouldResumeAndSaveSession() {
        // Disabling session resumptions for now, so the AI doesn't confuse the player with the actual peer
        return false;
    }

    @Override
    public boolean sendStatusUpdates() {
        return false;
    }

    // -------------------------------------------------------------------------
    // Audio hold / release
    // -------------------------------------------------------------------------

    public void holdAudio() {
        holdAudio = true;
    }

    public void releaseHeldAudio() {
        List<AudioChunk> toFlush;
        synchronized (heldAudioChunks) {
            toFlush = new ArrayList<>(heldAudioChunks);
            heldAudioChunks.clear();
            holdAudio = false;
        }

        if (toFlush.isEmpty()) {
            if (generationComplete) {
                onConversationEnded();
            }
            return;
        }

        for (AudioChunk chunk : toFlush) {
            super.onGeneratedAudio(chunk.data(), chunk.sampleRate());
        }
    }

    @Override
    public void onGeneratedAudio(byte[] data, int sampleRate) {
        synchronized (heldAudioChunks) {
            if (holdAudio) {
                heldAudioChunks.add(new AudioChunk(data, sampleRate));
                return;
            }
        }
        super.onGeneratedAudio(data, sampleRate);
    }

    @Override
    protected void onStreamPause() {
        if (generationComplete && holdAudio) {
            return;
        }
        super.onStreamPause();
    }

}
