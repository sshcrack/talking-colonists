package me.sshcrack.mc_talking.internal.session;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Serializes readiness checks, enqueue and drain for provider input.
 *
 * <p>A failed send retains the unsent head. Inputs may optionally carry a monotonic expiry so
 * microphone data accepted immediately before setup/recovery cannot be replayed much later as stale
 * speech. Non-expiring text keeps the original behavior.</p>
 */
public final class ProviderInputBuffer {
    public record DrainResult(int sent, int droppedExpired, int remaining) {
    }

    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private boolean closed;

    public synchronized DrainResult submit(String frame, BooleanSupplier ready, Consumer<String> send) {
        return submit(frame, Long.MAX_VALUE, System::nanoTime, ready, send);
    }

    /**
     * Adds an input with a maximum queue age measured by {@code nanoClock}. A value of
     * {@link Long#MAX_VALUE} disables expiry.
     */
    public synchronized DrainResult submit(
            String frame,
            long maxQueueAgeNanos,
            LongSupplier nanoClock,
            BooleanSupplier ready,
            Consumer<String> send
    ) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(nanoClock, "nanoClock");
        Objects.requireNonNull(ready, "ready");
        Objects.requireNonNull(send, "send");
        if (maxQueueAgeNanos <= 0 && maxQueueAgeNanos != Long.MAX_VALUE) {
            throw new IllegalArgumentException("maxQueueAgeNanos must be positive or Long.MAX_VALUE");
        }
        if (closed) return new DrainResult(0, 0, 0);

        long now = nanoClock.getAsLong();
        long expiresAt = maxQueueAgeNanos == Long.MAX_VALUE
                ? Long.MAX_VALUE
                : saturatedAdd(now, maxQueueAgeNanos);
        pending.addLast(new Pending(frame, expiresAt));
        return flushLocked(ready, send, nanoClock);
    }

    public synchronized void enqueueIfAbsent(String frame) {
        if (closed) return;
        for (Pending input : pending) {
            if (input.frame.equals(frame)) return;
        }
        pending.addLast(new Pending(frame, Long.MAX_VALUE));
    }

    public synchronized DrainResult flush(BooleanSupplier ready, Consumer<String> send) {
        return flush(ready, send, System::nanoTime);
    }

    public synchronized DrainResult flush(BooleanSupplier ready, Consumer<String> send, LongSupplier nanoClock) {
        Objects.requireNonNull(ready, "ready");
        Objects.requireNonNull(send, "send");
        Objects.requireNonNull(nanoClock, "nanoClock");
        return flushLocked(ready, send, nanoClock);
    }

    public synchronized void close() {
        closed = true;
        pending.clear();
    }

    public synchronized int size() {
        return pending.size();
    }

    private DrainResult flushLocked(BooleanSupplier ready, Consumer<String> send, LongSupplier nanoClock) {
        int sent = 0;
        int dropped = 0;
        while (!closed && !pending.isEmpty()) {
            Pending head = pending.getFirst();
            if (expired(head, nanoClock.getAsLong())) {
                pending.removeFirst();
                dropped++;
                continue;
            }
            if (!ready.getAsBoolean()) break;
            send.accept(head.frame);
            pending.removeFirst();
            sent++;
        }
        return new DrainResult(sent, dropped, pending.size());
    }

    private static boolean expired(Pending input, long now) {
        return input.expiresAtNanos != Long.MAX_VALUE && now >= input.expiresAtNanos;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private record Pending(String frame, long expiresAtNanos) {
    }
}
