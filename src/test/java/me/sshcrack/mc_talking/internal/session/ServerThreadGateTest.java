package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerThreadGateTest {
    @Test
    void worldActionRunsOnTheServerExecutor() throws Exception {
        var server = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-server"));
        try {
            var gate = new ServerThreadGate(server, () -> Thread.currentThread().getName().equals("test-server"));
            assertTrue(gate.call(() -> true, () -> Thread.currentThread().getName().equals("test-server"), 2000));
        } finally { server.shutdownNow(); }
    }

    @Test
    void timedOutQueuedActionCannotMutateTheWorldLater() {
        var queued = new ArrayDeque<Runnable>();
        var mutated = new AtomicBoolean();
        var gate = new ServerThreadGate(queued::add, () -> false);
        assertThrows(TimeoutException.class,
                () -> gate.call(() -> true, () -> mutated.compareAndSet(false, true), 10));
        queued.removeFirst().run();
        assertFalse(mutated.get());
    }
}
