package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A {@link GeminiWsClient} that represents one participant in a Live-WebSocket
 * citizen-to-citizen conversation.
 *
 * <p>After one participant's audible output has fully drained, its output transcript is forwarded
 * to the peer as text. The peer never receives a speculative transcript or held audio for speech
 * that the listener did not actually hear.</p>
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

    /** Peer client that receives the just-heard transcript as its next input. */
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
    private final AtomicBoolean endedNotified = new AtomicBoolean(false);

    /**
     * The citizen entity this client represents.
     */
    private final AbstractEntityCitizen citizen;
    private final CitizenPromptView promptView;

    // -------------------------------------------------------------------------

    public LiveConversationWsClient(
            AudioProvider audioProvider,
            AbstractEntityCitizen citizen,
            CitizenPromptView promptView,
            AtomicInteger sharedTurnCounter,
            Consumer<LiveConversationWsClient> onEnded) {
        this(audioProvider, citizen, promptView, sharedTurnCounter, onEnded, null);
    }

    public LiveConversationWsClient(
            AudioProvider audioProvider,
            AbstractEntityCitizen citizen,
            CitizenPromptView promptView,
            AtomicInteger sharedTurnCounter,
            Consumer<LiveConversationWsClient> onEnded,
            @Nullable String systemPromptAddition) {
        super(audioProvider, citizen);
        this.citizen = citizen;
        this.promptView = promptView;
        this.sharedTurnCounter = sharedTurnCounter;
        this.onEnded = onEnded;
        this.systemPromptAddition = systemPromptAddition;
    }

    // -------------------------------------------------------------------------
    // Peer wiring
    // -------------------------------------------------------------------------

    /** Sets the other participant who will receive this client's heard transcript. */
    public void setPeer(@Nullable LiveConversationWsClient peer) {
        this.peer = peer;
    }

    // -------------------------------------------------------------------------
    // GeminiWsClient contract
    // -------------------------------------------------------------------------

    /**
     * Citizen-to-citizen live conversations use TEXT_AND_AUDIO so an output transcript can be
     * forwarded to the peer after the exact audible turn drains.
     */
    @Override
    protected ModalityModes getEffectiveModality() {
        return ModalityModes.TEXT_AND_AUDIO;
    }

    @Override
    protected String getSystemPrompt() {
        var prompt = PromptRuntime.generateSystemControlledRoleplayPrompt(promptView);
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
    protected String getModelName() {
        return McTalkingConfig.INSTANCE.instance().currentAiModel.getName();
    }

    @Override
    protected void onQuotaExceededEvent(String message) {
        McTalking.LOGGER.warn("[LiveConvWs] Quota exceeded for {}: {}", citizen.getCitizenData().getName(), message);
        AiStatusHelper.setAiStatusSynced(citizen, AiStatus.QUOTA_EXCEEDED);
        notifyEnded();
    }

    @Override
    protected void onErrorEvent(Exception ex) {
        McTalking.LOGGER.error("[LiveConvWs] Error for {}", citizen.getCitizenData().getName(), ex);
        AiStatusHelper.setAiStatusSynced(citizen, AiStatus.ERROR);
        notifyEnded();
    }

    /**
     * Forwards a turn only after its local voice-chat playback has actually drained. This keeps the
     * peer's provider context aligned with what was heard and removes the old hidden audio buffer
     * that could otherwise resurrect a reply after interruption.
     */
    @Override
    protected void onAudibleTranscriptComplete(String transcript) {
        if (shouldEndConversation) return;

        int turn = sharedTurnCounter.incrementAndGet();
        McTalking.LOGGER.info("[LiveConvWs] Audible turn {} of {} completed by {}",
                turn, MAX_TOTAL_TURNS, citizen.getCitizenData().getName());
        if (turn >= MAX_TOTAL_TURNS) {
            McTalking.LOGGER.info("[LiveConvWs] Max turns reached after audible playback – ending conversation");
            notifyEnded();
            return;
        }

        LiveConversationWsClient currentPeer = peer;
        if (currentPeer != null && !currentPeer.isClosed()) {
            McTalking.LOGGER.info("[LiveConvWs] Forwarding heard transcript to {}",
                    currentPeer.getEntity().getCitizenData().getName());
            currentPeer.addPromptTextImmediate(transcript);
        }
    }

    @Override
    protected void onConversationEnded() {
        super.onConversationEnded();
        if (shouldEndConversation) {
            McTalking.LOGGER.info("[LiveConvWs] Final audio drained for {}; ending pair",
                    citizen.getCitizenData().getName());
        }
    }


    private void notifyEnded() {
        if (endedNotified.compareAndSet(false, true)) {
            onEnded.accept(this);
        }
    }

    @Override
    public void close() {
        super.close();
        notifyEnded();
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

}
