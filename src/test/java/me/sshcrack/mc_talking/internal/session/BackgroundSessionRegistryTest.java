package me.sshcrack.mc_talking.internal.session;

import me.sshcrack.mc_talking.util.BackgroundSlotType;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundSessionRegistryTest {
    @Test
    void staleReleaseCannotCloseReplacementBackgroundClient() {
        AtomicLong clock = new AtomicLong();
        var registry = registry(1, clock);
        UUID citizen = UUID.randomUUID();

        var old = registry.reserve(citizen, BackgroundSlotType.PREGEN);
        FakeClient oldClient = new FakeClient();
        assertNotNull(old);
        assertTrue(registry.attach(old, oldClient));
        assertTrue(registry.release(old));
        assertEquals(1, oldClient.closeCalls);

        var replacement = registry.reserve(citizen, BackgroundSlotType.PREGEN);
        FakeClient replacementClient = new FakeClient();
        assertNotNull(replacement);
        assertTrue(registry.attach(replacement, replacementClient));

        assertFalse(registry.release(old));
        assertTrue(registry.isActive(replacement));
        assertEquals(0, replacementClient.closeCalls);
        assertEquals(1, registry.size());
    }

    @Test
    void compactionMayEvictPregenButPregenNeverEvicts() {
        AtomicLong clock = new AtomicLong();
        var registry = registry(1, clock);
        UUID firstCitizen = UUID.randomUUID();
        FakeClient pregenClient = new FakeClient();
        var pregen = registry.reserve(firstCitizen, BackgroundSlotType.PREGEN);
        assertNotNull(pregen);
        registry.attach(pregen, pregenClient);

        assertNull(registry.reserve(UUID.randomUUID(), BackgroundSlotType.PREGEN));
        assertEquals(0, pregenClient.closeCalls);

        var compaction = registry.reserve(UUID.randomUUID(), BackgroundSlotType.COMPACTION);
        assertNotNull(compaction);
        assertEquals(1, pregenClient.closeCalls);
        assertFalse(registry.isActive(pregen));
        assertTrue(registry.isActive(compaction));
    }

    @Test
    void deadlineAndShutdownReturnCapacityToBaseline() {
        AtomicLong clock = new AtomicLong();
        var registry = registry(2, clock);
        FakeClient timedOut = new FakeClient();
        var first = registry.reserve(UUID.randomUUID(), BackgroundSlotType.PREGEN);
        var second = registry.reserve(UUID.randomUUID(), BackgroundSlotType.COMPACTION);
        registry.attach(first, timedOut);

        clock.set(101);
        var expired = registry.purge();
        assertEquals(1, expired.size());
        assertTrue(expired.get(0).deadlineExceeded());
        assertEquals(1, timedOut.closeCalls);
        assertEquals(1, registry.size());

        assertEquals(1, registry.shutdown());
        assertEquals(0, registry.size());
        assertEquals(0, registry.shutdown());
    }

    private static BackgroundSessionRegistry<FakeClient> registry(int capacity, AtomicLong clock) {
        AtomicInteger max = new AtomicInteger(capacity);
        return new BackgroundSessionRegistry<>(
                max::get,
                clock::get,
                type -> type == BackgroundSlotType.PREGEN ? 100 : 1_000,
                FakeClient::close,
                client -> client.closed
        );
    }

    private static final class FakeClient {
        boolean closed;
        int closeCalls;

        void close() {
            closeCalls++;
            closed = true;
        }
    }
}
