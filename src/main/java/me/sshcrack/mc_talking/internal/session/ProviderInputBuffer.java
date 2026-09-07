package me.sshcrack.mc_talking.internal.session;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Serializes readiness checks, enqueue and drain. A failed send retains the unsent head. */
public final class ProviderInputBuffer {
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private boolean closed;

    public synchronized void submit(String frame, BooleanSupplier ready, Consumer<String> send) {
        if (closed) return;
        pending.addLast(frame);
        flush(ready, send);
    }

    public synchronized void enqueueIfAbsent(String frame) {
        if (!closed && !pending.contains(frame)) pending.addLast(frame);
    }

    public synchronized void flush(BooleanSupplier ready, Consumer<String> send) {
        while (!closed && !pending.isEmpty() && ready.getAsBoolean()) {
            send.accept(pending.getFirst());
            pending.removeFirst();
        }
    }

    public synchronized void close() {
        closed = true;
        pending.clear();
    }
}
