package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenActivityRegistryTest {
    @Test
    void expiredAddonHandleCannotReleaseReplacementLease() {
        AtomicLong clock = new AtomicLong(100);
        var registry = new CitizenActivityRegistry(clock::get);
        UUID citizen = UUID.randomUUID();
        UUID oldToken = UUID.randomUUID();
        UUID replacementToken = UUID.randomUUID();

        assertTrue(registry.reserveAddon(citizen, oldToken, false, 10));
        clock.addAndGet(11);
        assertFalse(registry.isActive(citizen, oldToken, CitizenActivityRegistry.OwnerKind.ADDON));
        assertTrue(registry.reserveAddon(citizen, replacementToken, false, 100));

        assertFalse(registry.release(citizen, oldToken, CitizenActivityRegistry.OwnerKind.ADDON));
        assertTrue(registry.isBusy(citizen));
        assertTrue(registry.isActive(citizen, replacementToken, CitizenActivityRegistry.OwnerKind.ADDON));
        assertEquals(1, registry.size());
    }

    @Test
    void playerTakeoverInvalidatesAddonAndRunsCoreCleanupExactlyOnce() {
        AtomicLong clock = new AtomicLong();
        var registry = new CitizenActivityRegistry(clock::get);
        UUID citizen = UUID.randomUUID();
        UUID addonToken = UUID.randomUUID();

        assertTrue(registry.reserveAddon(citizen, addonToken, false, 1_000));
        assertNull(registry.preemptForPlayer(citizen));
        assertFalse(registry.isActive(citizen, addonToken, CitizenActivityRegistry.OwnerKind.ADDON));
        assertFalse(registry.isBusy(citizen));

        AtomicInteger cleanupCalls = new AtomicInteger();
        var core = registry.reserveCore(citizen, false, 1_000, () -> { }, cleanupCalls::incrementAndGet);
        assertNotNull(core);
        Runnable cleanup = registry.preemptForPlayer(citizen);
        assertNotNull(cleanup);
        assertFalse(registry.isBusy(citizen));
        cleanup.run();
        assertEquals(1, cleanupCalls.get());
        assertFalse(registry.release(citizen, core.ownershipId(), core.ownerKind()));
    }

    @Test
    void shutdownReturnsCoreCleanupAndClearsAllBusyState() {
        AtomicLong clock = new AtomicLong();
        var registry = new CitizenActivityRegistry(clock::get);
        AtomicInteger coreCleanupCalls = new AtomicInteger();
        UUID coreCitizen = UUID.randomUUID();
        UUID addonCitizen = UUID.randomUUID();

        assertNotNull(registry.reserveCore(coreCitizen, false, 5_000, () -> { }, coreCleanupCalls::incrementAndGet));
        assertTrue(registry.reserveAddon(addonCitizen, UUID.randomUUID(), false, 5_000));
        var callbacks = registry.shutdown();
        assertEquals(1, callbacks.size());
        callbacks.forEach(Runnable::run);

        assertEquals(1, coreCleanupCalls.get());
        assertEquals(0, registry.size());
        assertFalse(registry.isBusy(coreCitizen));
        assertFalse(registry.isBusy(addonCitizen));
        assertTrue(registry.shutdown().isEmpty());
    }
    @Test
    void expiredCoreCleanupIsNotLostToOpportunisticBusyChecks() {
        AtomicLong clock = new AtomicLong();
        var registry = new CitizenActivityRegistry(clock::get);
        UUID citizen = UUID.randomUUID();
        AtomicInteger timeoutCalls = new AtomicInteger();

        assertNotNull(registry.reserveCore(citizen, false, 10, timeoutCalls::incrementAndGet, () -> { }));
        clock.set(11);
        assertTrue(registry.isBusy(citizen), "core lease stays owned until its timeout hook can be delivered");
        assertEquals(0, timeoutCalls.get());

        var callbacks = registry.purgeExpired();
        assertEquals(1, callbacks.size());
        callbacks.forEach(Runnable::run);
        assertEquals(1, timeoutCalls.get());
        assertFalse(registry.isBusy(citizen));
        assertTrue(registry.purgeExpired().isEmpty());
    }

}
