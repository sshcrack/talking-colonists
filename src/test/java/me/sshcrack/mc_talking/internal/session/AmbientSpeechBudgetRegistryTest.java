package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic tests for the per-player ambient speech budget using a fake clock. Mirrors the
 * scenario from roadmap Q4 / issue #133: a busy colony with many citizens greeting, mumbling,
 * gossiping, and randomly chatting near a player should not let that player hear more than
 * {@code maxLines} ambient lines per rolling window, regardless of which handler/kind produced
 * the line.
 */
class AmbientSpeechBudgetRegistryTest {
    @Test
    void budgetIsEnforcedAcrossDifferentAmbientKinds() {
        AtomicLong clock = new AtomicLong(0);
        var registry = new AmbientSpeechBudgetRegistry(clock::get);
        UUID player = UUID.randomUUID();

        // Simulate a greeting, a mumble, and a voiced rumor all near the same player within one
        // window (default-like budget: 3 lines / 60s).
        assertTrue(registry.tryConsume(Set.of(player), 3, 60_000)); // greeting
        assertTrue(registry.tryConsume(Set.of(player), 3, 60_000)); // mumble
        assertTrue(registry.tryConsume(Set.of(player), 3, 60_000)); // voiced rumor

        // A 4th ambient line (e.g. a voiced broadcast) within the same window must be skipped.
        assertFalse(registry.tryConsume(Set.of(player), 3, 60_000));
    }

    @Test
    void rollingWindowExpiresOldestUsageFirst() {
        AtomicLong clock = new AtomicLong(0);
        var registry = new AmbientSpeechBudgetRegistry(clock::get);
        UUID player = UUID.randomUUID();

        assertTrue(registry.tryConsume(Set.of(player), 2, 1_000));
        clock.set(400);
        assertTrue(registry.tryConsume(Set.of(player), 2, 1_000));
        // Budget exhausted with both lines still inside the window.
        clock.set(900);
        assertFalse(registry.tryConsume(Set.of(player), 2, 1_000));

        // The first line (t=0) falls out of the rolling window at t=1000, freeing one slot.
        clock.set(1_001);
        assertTrue(registry.tryConsume(Set.of(player), 2, 1_000));
        // But the second line (t=400) is still within the window from t=1001, so budget is spent again.
        assertFalse(registry.tryConsume(Set.of(player), 2, 1_000));
    }

    @Test
    void hasCapacityPeeksWithoutConsumingBudget() {
        AtomicLong clock = new AtomicLong(0);
        var registry = new AmbientSpeechBudgetRegistry(clock::get);
        UUID player = UUID.randomUUID();

        assertTrue(registry.tryConsume(Set.of(player), 1, 60_000));
        // Peeking repeatedly (as a handler scanning many candidate citizens would) never spends
        // budget on its own.
        for (int i = 0; i < 10; i++) {
            assertFalse(registry.hasCapacity(Set.of(player), 1, 60_000));
        }
        assertFalse(registry.tryConsume(Set.of(player), 1, 60_000));
    }

    @Test
    void multiplePlayersInRangeAreTrackedIndependently() {
        AtomicLong clock = new AtomicLong(0);
        var registry = new AmbientSpeechBudgetRegistry(clock::get);
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        // Exhaust the budget for player A only.
        assertTrue(registry.tryConsume(Set.of(playerA), 1, 60_000));
        assertFalse(registry.tryConsume(Set.of(playerA), 1, 60_000));

        // Player B, standing elsewhere, is unaffected.
        assertTrue(registry.tryConsume(Set.of(playerB), 1, 60_000));
    }

    @Test
    void lineHeardByMultiplePlayersIsSkippedEntirelyIfAnyOneIsOverBudget() {
        AtomicLong clock = new AtomicLong(0);
        var registry = new AmbientSpeechBudgetRegistry(clock::get);
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        assertTrue(registry.tryConsume(Set.of(playerA), 1, 60_000));

        // A line heard by both A (over budget) and B (fresh) must be skipped for both — it is not
        // partially charged against B just because A ran out first.
        assertFalse(registry.tryConsume(Set.of(playerA, playerB), 1, 60_000));

        // Because the rejected attempt recorded nothing, B still has full budget available.
        assertTrue(registry.tryConsume(Set.of(playerB), 1, 60_000));
    }

    @Test
    void emptyHearingSetIsAlwaysAllowedAndConsumesNothing() {
        AtomicLong clock = new AtomicLong(0);
        var registry = new AmbientSpeechBudgetRegistry(clock::get);
        assertTrue(registry.tryConsume(List.of(), 1, 60_000));
        assertTrue(registry.hasCapacity(List.of(), 1, 60_000));
    }

    @Test
    void clearResetsAllRecordedUsage() {
        AtomicLong clock = new AtomicLong(0);
        var registry = new AmbientSpeechBudgetRegistry(clock::get);
        UUID player = UUID.randomUUID();

        assertTrue(registry.tryConsume(Set.of(player), 1, 60_000));
        assertFalse(registry.tryConsume(Set.of(player), 1, 60_000));

        registry.clear();
        assertTrue(registry.tryConsume(Set.of(player), 1, 60_000));
    }

    @Test
    void invalidArgumentsAreRejected() {
        AtomicLong clock = new AtomicLong(0);
        var registry = new AmbientSpeechBudgetRegistry(clock::get);
        UUID player = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> registry.tryConsume(Set.of(player), 0, 60_000));
        assertThrows(IllegalArgumentException.class, () -> registry.tryConsume(Set.of(player), 1, 0));
        assertThrows(NullPointerException.class, () -> registry.tryConsume(null, 1, 60_000));
    }

    @Test
    void exactlyAtWindowBoundaryCountsAsExpired() {
        AtomicLong clock = new AtomicLong(0);
        var registry = new AmbientSpeechBudgetRegistry(clock::get);
        UUID player = UUID.randomUUID();

        assertTrue(registry.tryConsume(Set.of(player), 1, 1_000));
        clock.set(1_000);
        // now - timestamp == windowMillis is treated as expired (window is a half-open interval).
        assertEquals(1_000L, clock.get());
        assertTrue(registry.tryConsume(Set.of(player), 1, 1_000));
    }
}
