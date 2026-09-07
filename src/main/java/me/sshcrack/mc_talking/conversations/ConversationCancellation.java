package me.sshcrack.mc_talking.conversations;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Coordinates terminal cancellation with a mode-specific live-session cleanup action. */
final class ConversationCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Runnable> liveCancellation = new AtomicReference<>();

    boolean isCancelled() {
        return cancelled.get();
    }

    void cancel() {
        cancelled.set(true);
        Runnable action = liveCancellation.getAndSet(null);
        if (action != null) action.run();
    }

    /**
     * Registers the current Live cleanup action. If cancellation already won the race, the action
     * runs immediately instead of allowing a provider session to start after the handle ended.
     */
    boolean registerLiveCancellation(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (cancelled.get()) {
            action.run();
            return false;
        }
        if (!liveCancellation.compareAndSet(null, action)) {
            throw new IllegalStateException("A Live cancellation action is already registered");
        }
        if (cancelled.get()) {
            if (liveCancellation.compareAndSet(action, null)) action.run();
            return false;
        }
        return true;
    }

    void clearLiveCancellation(Runnable action) {
        liveCancellation.compareAndSet(action, null);
    }
}
