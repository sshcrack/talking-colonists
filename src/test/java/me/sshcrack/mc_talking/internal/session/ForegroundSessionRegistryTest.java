package me.sshcrack.mc_talking.internal.session;

import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForegroundSessionRegistryTest {
    @Test
    void partialStartupReturnsCapacityAndBusyStateToBaseline() {
        AtomicInteger capacity = new AtomicInteger(1);
        AtomicLong clock = new AtomicLong(100);
        List<ForegroundSessionRegistry.EndedSession<String, FakeClient>> ended = new ArrayList<>();
        var registry = registry(capacity, clock, ended);
        UUID citizen = UUID.randomUUID();

        var reservation = registry.reserve(
                citizen, "citizen", ConversationKind.MUMBLE,
                ForegroundSessionRegistry.Priority.AMBIENT, null);
        assertTrue(reservation.granted());
        FakeClient client = new FakeClient();
        assertTrue(registry.attachClient(reservation.token(), client));
        assertEquals(1, registry.usedSlots());
        assertTrue(registry.isBusy(citizen));

        assertTrue(registry.end(
                reservation.token(),
                ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                "constructor/connect failed"));

        assertEquals(0, registry.usedSlots());
        assertFalse(registry.isBusy(citizen));
        assertEquals(1, client.closeCalls);
        assertEquals(1, ended.size());
        assertEquals(ForegroundSessionRegistry.State.STARTING, ended.get(0).previousState());
        assertEquals(ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED, ended.get(0).reason());
    }

    @Test
    void repeatedEndClosesOwnedClientExactlyOnce() {
        AtomicInteger capacity = new AtomicInteger(1);
        AtomicLong clock = new AtomicLong();
        var registry = registry(capacity, clock, new ArrayList<>());
        UUID citizen = UUID.randomUUID();
        var reservation = registry.reserve(
                citizen, "citizen", ConversationKind.ADDON_AMBIENT,
                ForegroundSessionRegistry.Priority.AMBIENT, null);
        FakeClient client = new FakeClient();
        assertTrue(registry.attachClient(reservation.token(), client));
        assertTrue(registry.markActive(reservation.token(), "active"));

        assertTrue(registry.end(reservation.token(), ForegroundSessionRegistry.TerminalReason.CANCELLED, "cancel"));
        assertFalse(registry.end(reservation.token(), ForegroundSessionRegistry.TerminalReason.CANCELLED, "late close"));

        assertEquals(1, client.closeCalls);
        assertEquals(0, registry.usedSlots());
        assertFalse(registry.isBusy(citizen));
    }

    @Test
    void staleCallbackCannotCloseOrReleaseReplacement() {
        AtomicInteger capacity = new AtomicInteger(1);
        AtomicLong clock = new AtomicLong();
        var registry = registry(capacity, clock, new ArrayList<>());
        UUID citizen = UUID.randomUUID();

        var oldReservation = registry.reserve(
                citizen, "old", ConversationKind.MUMBLE,
                ForegroundSessionRegistry.Priority.AMBIENT, null);
        FakeClient oldClient = new FakeClient();
        assertTrue(registry.attachClient(oldReservation.token(), oldClient));
        assertTrue(registry.markActive(oldReservation.token(), "old active"));
        assertTrue(registry.end(oldReservation.token(), ForegroundSessionRegistry.TerminalReason.REPLACED, "takeover"));

        var replacement = registry.reserve(
                citizen, "replacement", ConversationKind.PLAYER,
                ForegroundSessionRegistry.Priority.PLAYER, UUID.randomUUID());
        FakeClient replacementClient = new FakeClient();
        assertTrue(replacement.granted());
        assertTrue(registry.attachClient(replacement.token(), replacementClient));
        assertTrue(registry.markActive(replacement.token(), "replacement active"));

        assertFalse(registry.end(oldReservation.token(), ForegroundSessionRegistry.TerminalReason.COMPLETED, "stale callback"));
        assertTrue(registry.isBusy(citizen));
        assertEquals(1, registry.usedSlots());
        assertSame(replacementClient, registry.client(citizen));
        assertEquals(0, replacementClient.closeCalls);

        assertTrue(registry.end(replacement.token(), ForegroundSessionRegistry.TerminalReason.COMPLETED, "done"));
        assertEquals(0, registry.usedSlots());
    }

    @Test
    void playerPreemptionEvictsOldestAmbientButAmbientNeverEvicts() {
        AtomicInteger capacity = new AtomicInteger(1);
        AtomicLong clock = new AtomicLong();
        List<ForegroundSessionRegistry.EndedSession<String, FakeClient>> ended = new ArrayList<>();
        var registry = registry(capacity, clock, ended);
        UUID ambientCitizen = UUID.randomUUID();
        var ambient = registry.reserve(
                ambientCitizen, "ambient", ConversationKind.MUMBLE,
                ForegroundSessionRegistry.Priority.AMBIENT, null);
        FakeClient ambientClient = new FakeClient();
        registry.attachClient(ambient.token(), ambientClient);
        registry.markActive(ambient.token(), "ambient active");

        var blockedAmbient = registry.reserve(
                UUID.randomUUID(), "blocked", ConversationKind.CITIZEN_PAIR,
                ForegroundSessionRegistry.Priority.AMBIENT, null);
        assertFalse(blockedAmbient.granted());
        assertEquals(ForegroundSessionRegistry.RejectionReason.CAPACITY_EXHAUSTED, blockedAmbient.rejectionReason());
        assertEquals(0, ambientClient.closeCalls);

        UUID playerId = UUID.randomUUID();
        UUID playerCitizen = UUID.randomUUID();
        var player = registry.reserve(
                playerCitizen, "player", ConversationKind.PLAYER,
                ForegroundSessionRegistry.Priority.PLAYER, playerId);

        assertTrue(player.granted());
        assertEquals(1, ambientClient.closeCalls);
        assertFalse(registry.isBusy(ambientCitizen));
        assertTrue(registry.isBusy(playerCitizen));
        assertEquals(ForegroundSessionRegistry.TerminalReason.EVICTED, ended.get(0).reason());
    }

    @Test
    void controlledReservationCarriesStableSessionAndTurnIdentityIntoLifecycleSnapshot() {
        var registry = registry(new AtomicInteger(1), new AtomicLong(), new ArrayList<>());
        UUID citizen = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();

        var reservation = registry.reserve(
                citizen, "controlled", ConversationKind.CONTROLLED,
                ForegroundSessionRegistry.Priority.AMBIENT, null, sessionId, turnId);

        assertTrue(reservation.granted());
        assertEquals(sessionId, reservation.snapshot().sessionId());
        assertEquals(turnId, reservation.snapshot().turnId());
        var current = registry.snapshot(citizen).orElseThrow();
        assertEquals(sessionId, current.sessionId());
        assertEquals(turnId, current.turnId());
    }

    @Test
    void shutdownReleasesEveryClientOnceAndRecordsTerminalDiagnostics() {
        AtomicInteger capacity = new AtomicInteger(2);
        AtomicLong clock = new AtomicLong(10);
        var registry = registry(capacity, clock, new ArrayList<>());
        FakeClient firstClient = new FakeClient();
        FakeClient secondClient = new FakeClient();

        var first = registry.reserve(
                UUID.randomUUID(), "a", ConversationKind.MUMBLE,
                ForegroundSessionRegistry.Priority.AMBIENT, null);
        var second = registry.reserve(
                UUID.randomUUID(), "b", ConversationKind.CITIZEN_PAIR,
                ForegroundSessionRegistry.Priority.AMBIENT, null);
        registry.attachClient(first.token(), firstClient);
        registry.attachClient(second.token(), secondClient);
        registry.markActive(first.token(), "active");
        registry.markRecovering(second.token(), 3, "transient provider close");

        assertEquals(2, registry.shutdown());
        assertEquals(0, registry.usedSlots());
        assertEquals(1, firstClient.closeCalls);
        assertEquals(1, secondClient.closeCalls);
        assertEquals(2, registry.terminalHistory().size());
        assertTrue(registry.terminalHistory().stream().allMatch(
                diagnostic -> diagnostic.reason() == ForegroundSessionRegistry.TerminalReason.SERVER_SHUTDOWN));
        assertEquals(3, registry.lastTerminal(second.token().citizenId()).orElseThrow().recoveryAttempts());

        assertEquals(0, registry.shutdown());
        assertEquals(1, firstClient.closeCalls);
        assertEquals(1, secondClient.closeCalls);
    }

    private static ForegroundSessionRegistry<String, FakeClient> registry(
            AtomicInteger capacity,
            AtomicLong clock,
            List<ForegroundSessionRegistry.EndedSession<String, FakeClient>> ended
    ) {
        return new ForegroundSessionRegistry<>(
                capacity::get,
                clock::get,
                FakeClient::close,
                client -> client.closed,
                ended::add
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
