package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Addon-owned floor-control session for meetings, councils and ceremonies.
 *
 * <p>Talking Colonists owns provider connections, speech playback and capacity. The caller owns
 * movement/seating and calls {@link #requestTurn} only after the selected speaker has arrived.
 * At most one turn is active. The returned future completes after audible playback finishes, not
 * merely when Gemini finishes generating.</p>
 */
public interface ControlledConversationSession extends AutoCloseable {
    enum State { OPEN, TURN_ACTIVE, ENDED }

    @NotNull List<AbstractEntityCitizen> participants();

    @NotNull State state();

    /** Replaces the agenda/context used for subsequent turns. */
    void setAgenda(@NotNull String agenda);

    /** Adds a player-authored statement to the bounded shared transcript. */
    void addPlayerStatement(@NotNull ServerPlayer player, @NotNull String statement);

    /**
     * Gives one registered participant the floor. The caller should invoke this only after its own
     * navigation/arrival checks succeed.
     */
    @NotNull CompletableFuture<AmbientLineResult> requestTurn(
            @NotNull AbstractEntityCitizen speaker,
            @NotNull String topicOrInstruction
    );

    /** Interrupts only the current controlled turn; the session remains open. */
    boolean interruptTurn();

    /** Ends the session and cancels a current turn if present. Idempotent. */
    void end();

    /** Human-readable bounded shared transcript snapshot used to ground later turns. */
    @NotNull String sharedTranscript();

    @Override
    default void close() {
        end();
    }
}
