package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Addon-owned floor-control session for meetings, councils and ceremonies.
 *
 * <p>Public calls may originate on any thread. World/provider work and terminal callback completion
 * are marshalled to the Minecraft server thread. Silent attendees reserve no provider connection;
 * only the active speaker consumes foreground provider capacity. Exactly one turn may be active.</p>
 *
 * <p>With no audio anchor, speech follows the citizen entity as it moves. A per-turn
 * {@link ControlledAudioAnchor} fixes playback at a podium/microphone position instead.</p>
 *
 * <p>Per-session addon-tool authority is selected by {@link ControlledConversationOptions} when the
 * session is created. Only allowed addon tools are advertised and executable. Tool calls receive
 * the same {@link #sessionId()} and current turn identity through their authoritative tool context.</p>
 */
public interface ControlledConversationSession extends AutoCloseable {
    enum State { OPEN, TURN_ACTIVE, ENDED }
    enum EndReason { COMPLETED, CALLER_CANCELLED, PLAYER_INTERRUPTED, SERVER_SHUTDOWN }

    /** Stable identity shared by all turns in this controlled session. */
    @NotNull UUID sessionId();

    @NotNull List<AbstractEntityCitizen> participants();
    @NotNull State state();

    /** Replaces agenda/context for subsequent turns; an already-requested turn keeps its snapshot. */
    void setAgenda(@NotNull String agenda);

    /** Adds a player-authored statement to bounded shared history with stable UUID attribution. */
    void addPlayerStatement(@NotNull ServerPlayer player, @NotNull String statement);

    /** Requests a moving-speaker turn. The future completes only after audible playback terminates. */
    default @NotNull CompletableFuture<ControlledTurnResult> requestTurn(
            @NotNull AbstractEntityCitizen speaker,
            @NotNull String topicOrInstruction
    ) {
        return requestTurn(speaker, topicOrInstruction, null);
    }

    /** Requests one turn, optionally spatialized at a fixed podium/microphone anchor. */
    @NotNull CompletableFuture<ControlledTurnResult> requestTurn(
            @NotNull AbstractEntityCitizen speaker,
            @NotNull String topicOrInstruction,
            @Nullable ControlledAudioAnchor audioAnchor
    );

    /** Immediately cancels current playback/generation and rejects all late work for that turn. */
    boolean interruptTurn();

    /** Ends the session and cancels a current turn if present. Idempotent. */
    void end(@NotNull EndReason reason);

    default void end() { end(EndReason.COMPLETED); }

    @NotNull List<ConversationTranscriptEntry> transcript();
    @NotNull String sharedTranscript();

    @Override
    default void close() { end(EndReason.CALLER_CANCELLED); }
}
