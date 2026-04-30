package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
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

    /** Maximum number of speaking turns (across both participants) before the session closes. */
    private static final int MAX_TOTAL_TURNS = 10;

    /** Peer client that will receive our generated audio as its input. */
    @Nullable
    private LiveConversationWsClient peer;

    /** Shared turn counter so both clients can agree when to stop. */
    private final AtomicInteger sharedTurnCounter;

    /** Callback invoked when this client's session ends (either naturally or on error). */
    private final Consumer<LiveConversationWsClient> onEnded;

    /** The citizen entity this client represents. */
    private final AbstractEntityCitizen citizen;

    /** Accumulated PCM that was generated during the last AI turn. */
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

    /** Sets the other participant who will receive this client's audio output. */
    public void setPeer(LiveConversationWsClient peer) {
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
            }
        }

        lastGeneratedPcm = new short[0];
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
        // Accumulate raw PCM (int16 little-endian) for peer forwarding
        // The parent converts byte[] → short[] internally; we do it here too.
        short[] chunk = bytesToShorts(data);
        short[] combined = new short[lastGeneratedPcm.length + chunk.length];
        System.arraycopy(lastGeneratedPcm, 0, combined, 0, lastGeneratedPcm.length);
        System.arraycopy(chunk, 0, combined, lastGeneratedPcm.length, chunk.length);
        lastGeneratedPcm = combined;

        // Let parent play the audio in-world
        super.onGeneratedAudio(data, sampleRate);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static short[] bytesToShorts(byte[] bytes) {
        short[] out = new short[bytes.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
        }
        return out;
    }
}
