package me.sshcrack.mc_talking.internal.session;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tracks the citizen's current spoken output turn of one provider session, and decides which
 * transcript counts as heard. A transcript is committed only when its exact turn finished
 * generating (provider turn complete), was not interrupted, and its audio drained. Barge-in,
 * provider interruption or invalidation drop it. Playback itself stays with the caller; this class
 * only holds the state, so the rules can be tested without audio.
 */
public final class OutputTurnTracker {
    /** Result of {@link #interruptForBargeIn(UUID)}: the cancelled turn, and whether the provider had already finished it. */
    public record BargeIn(UUID turnId, boolean providerAlreadyFinished) {
    }

    /** Result of {@link #completeAudible(UUID)}: whether the turn completed, and the heard transcript if any. */
    public record Completion(boolean completed, @Nullable String heardTranscript) {
        static final Completion NOT_COMPLETED = new Completion(false, null);
    }

    private final Supplier<UUID> ids;
    @Nullable private UUID turnId;
    private boolean interrupted;
    private volatile boolean providerTurnComplete;
    @Nullable private UUID pendingTranscriptTurnId;
    @Nullable private String pendingTranscript;

    public OutputTurnTracker() {
        this(UUID::randomUUID);
    }

    OutputTurnTracker(Supplier<UUID> ids) {
        this.ids = ids;
    }

    /** The current turn, starting a new one (and telling {@code onNewTurn}) when there is none. */
    public synchronized UUID ensure(Consumer<UUID> onNewTurn) {
        if (turnId == null) {
            turnId = ids.get();
            interrupted = false;
            providerTurnComplete = false;
            clearPending();
            onNewTurn.accept(turnId);
        }
        return turnId;
    }

    public synchronized @Nullable UUID current() {
        return turnId;
    }

    public synchronized boolean isInterrupted() {
        return interrupted;
    }

    public boolean isProviderTurnComplete() {
        return providerTurnComplete;
    }

    /** The provider reported the turn complete (all output generated). */
    public void markProviderTurnComplete() {
        providerTurnComplete = true;
    }

    /** The provider interrupted its own output: nothing of this turn counts as heard. */
    public synchronized void markInterrupted() {
        interrupted = true;
        clearPending();
    }

    /**
     * The player spoke over the citizen. Returns the interrupted turn, or null when there is no
     * turn (or it changed meanwhile).
     */
    public synchronized @Nullable BargeIn interruptForBargeIn(UUID expectedTurn) {
        if (!expectedTurn.equals(turnId)) return null;
        interrupted = true;
        clearPending();
        return new BargeIn(expectedTurn, providerTurnComplete);
    }

    /** Drops the current turn entirely (reconnect or close). Returns the dropped turn, if any. */
    public synchronized @Nullable UUID invalidate() {
        UUID previous = turnId;
        interrupted = true;
        providerTurnComplete = false;
        clearPending();
        turnId = null;
        return previous;
    }

    /** Remembers the transcript of {@code turn}; it is committed only if that turn completes audibly. */
    public synchronized void setPendingTranscript(UUID turn, String transcript) {
        pendingTranscriptTurnId = turn;
        pendingTranscript = transcript;
    }

    /**
     * Completes {@code turn} once its audio drained. Only the current, uninterrupted turn that the
     * provider finished completes; it then ends and its pending transcript (if any) is returned.
     */
    public synchronized Completion completeAudible(UUID turn) {
        if (!turn.equals(turnId) || interrupted || !providerTurnComplete) return Completion.NOT_COMPLETED;
        String heard = turn.equals(pendingTranscriptTurnId) ? pendingTranscript : null;
        clearPending();
        turnId = null;
        providerTurnComplete = false;
        return new Completion(true, heard);
    }

    /** Ends an interrupted {@code turn} so the next output starts a fresh one. Returns false if it is not current. */
    public synchronized boolean completeInterrupted(UUID turn) {
        if (!turn.equals(turnId)) return false;
        clearPending();
        turnId = null;
        providerTurnComplete = false;
        interrupted = false;
        return true;
    }

    private void clearPending() {
        pendingTranscriptTurnId = null;
        pendingTranscript = null;
    }
}
