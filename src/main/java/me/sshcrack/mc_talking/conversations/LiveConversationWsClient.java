package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import me.sshcrack.mc_talking.util.AudioHelper;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.TARGET_SAMPLE_RATE;
import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

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

    /**
     * Accumulated PCM that was generated during the last AI turn.
     */
    private volatile short[] lastGeneratedPcm = new short[0];

    // -------------------------------------------------------------------------

    public LiveConversationWsClient(
            AudioProvider audioProvider,
            AbstractEntityCitizen citizen,
            AtomicInteger sharedTurnCounter,
            Consumer<LiveConversationWsClient> onEnded) {
        super(audioProvider, citizen);
        this.citizen = citizen;
        this.sharedTurnCounter = sharedTurnCounter;
        this.onEnded = onEnded;
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

    @Override
    protected String getSystemPrompt() {
        Map<UUID, String> others = new HashMap<>();
        if (peer != null) {
            others.put(peer.citizen.getUUID(), peer.citizen.getCitizenData().getName());
        }
        var view = CitizenPromptViewFactory.create(citizen.getCitizenData(), others, null);
        return CitizenPromptService.generateSystemControlledRoleplayPrompt(view);
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
        McTalking.LOGGER.error("[LiveConvWs] Error for {}: {}", citizen.getCitizenData().getName(), ex.getMessage());
        AiStatusHelper.setAiStatusOnServerThread(citizen, AiStatus.ERROR);
        onEnded.accept(this);
    }

    /**
     * Called by the parent once audio generation for a turn is complete.
     * We forward the generated PCM to the peer so the conversation continues.
     */
    @Override
    protected void onConversationEnded() {
        super.onConversationEnded();

        int turn = sharedTurnCounter.incrementAndGet();
        McTalking.LOGGER.info("[LiveConvWs] Turn {} of {} completed by {}", turn, MAX_TOTAL_TURNS, citizen.getCitizenData().getName());

        if (turn >= MAX_TOTAL_TURNS) {
            McTalking.LOGGER.info("[LiveConvWs] Max turns reached – ending conversation");
            onEnded.accept(this);
            return;
        }

        // Hand our generated audio to the peer as its next prompt
        if (peer != null && !peer.isClosed()) {
            short[] audio = lastGeneratedPcm;
            if (audio.length > 0) {
                peer.addPromptAudio(audio);
                // Add 5 seconds of silence to trigger the AI to respond

                short[] ambientAudio = McTalkingVoicechatPlugin.generateAmbientNoise((int) (960 * 5000 / McTalkingVoicechatPlugin.SILENCE_INTERVAL_MS));
                peer.addPromptAudio(ambientAudio);
            }
        }

        lastGeneratedPcm = new short[0];
    }

    @Override
    public boolean shouldResumeAndSaveSession() {
        // Disabling session resumptions for now, so the AI doesn't confuse the player with the actual peer
        return false;
    }

    // -------------------------------------------------------------------------
    // Audio capture
    // -------------------------------------------------------------------------

    /**
     * Intercepts generated audio so we can relay it to the peer after the turn
     * ends, while still letting the parent class play it in-world.
     */
    @Override
    public void onGeneratedAudio(byte[] data, int sampleRate) {
        // Gemini gives us a different sample rate than the onGeneratedAudio expects
        short[] chunk = vcApi.getAudioConverter().bytesToShorts(data);
        short[] chunkResampled = AudioHelper.resampleAudio(chunk, sampleRate, TARGET_SAMPLE_RATE);

        short[] combined = new short[lastGeneratedPcm.length + chunkResampled.length];

        System.arraycopy(lastGeneratedPcm, 0, combined, 0, lastGeneratedPcm.length);
        System.arraycopy(chunkResampled, 0, combined, lastGeneratedPcm.length, chunkResampled.length);
        lastGeneratedPcm = combined;

        // Let parent play the audio in-world
        super.onGeneratedAudio(data, sampleRate);
    }

}
