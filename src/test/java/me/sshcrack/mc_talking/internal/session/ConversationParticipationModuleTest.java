package me.sshcrack.mc_talking.internal.session;

import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.network.AiStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationParticipationModuleTest {
    @Test
    void urgencySetupSpeechAndCompletionNeverAdvertiseListening() {
        Harness harness = new Harness();
        UUID citizen = UUID.randomUUID();
        var urgent = harness.reserveAmbient(citizen, ConversationKind.URGENT_CONTACT);

        harness.participation.providerConnecting(urgent.token());
        assertEquals(AiStatus.CONNECTING, harness.presentation(urgent.token()));

        harness.participation.providerReady(urgent.token());
        assertEquals(AiStatus.NONE, harness.presentation(urgent.token()));
        assertFalse(harness.participation.canRouteInput(urgent.token(), UUID.randomUUID()));

        harness.participation.playbackThinking(urgent.token());
        assertEquals(AiStatus.THINKING, harness.presentation(urgent.token()));
        harness.participation.playbackTalking(urgent.token());
        assertEquals(AiStatus.TALKING, harness.presentation(urgent.token()));
        harness.participation.playbackIdle(urgent.token());
        assertEquals(AiStatus.NONE, harness.presentation(urgent.token()));

        assertTrue(harness.registry.end(
                urgent.token(), ForegroundSessionRegistry.TerminalReason.COMPLETED, "announcement complete"));
        assertTrue(harness.participation.presentationIfCurrent(urgent.token()).isEmpty());
        assertTrue(harness.participation.canClearAfterCompletion(urgent.token()));
    }

    @Test
    void directPlayerSetupAdvertisesListeningOnlyAfterProviderReadiness() {
        Harness harness = new Harness();
        UUID player = UUID.randomUUID();
        var direct = harness.reservePlayer(UUID.randomUUID(), player);

        assertEquals(AiStatus.NONE, harness.presentation(direct.token()));
        assertFalse(harness.participation.canRouteInput(direct.token(), player));

        harness.participation.providerConnecting(direct.token());
        assertEquals(AiStatus.CONNECTING, harness.presentation(direct.token()));
        assertFalse(harness.participation.canRouteInput(direct.token(), player));

        harness.participation.providerReady(direct.token());
        assertEquals(AiStatus.LISTENING, harness.presentation(direct.token()));
        assertTrue(harness.participation.canRouteInput(direct.token(), player));
    }

    @Test
    void recoveryRemovesListeningPromiseUntilReadinessReturns() {
        Harness harness = new Harness();
        UUID player = UUID.randomUUID();
        var direct = harness.reservePlayer(UUID.randomUUID(), player);
        harness.participation.providerReady(direct.token());
        assertEquals(AiStatus.LISTENING, harness.presentation(direct.token()));
        assertTrue(harness.participation.canRouteInput(direct.token(), player));

        harness.participation.providerRecovering(direct.token());
        assertEquals(AiStatus.RECONNECTING, harness.presentation(direct.token()));
        assertFalse(harness.participation.canRouteInput(direct.token(), player));

        harness.participation.providerReady(direct.token());
        assertEquals(AiStatus.LISTENING, harness.presentation(direct.token()));
        assertTrue(harness.participation.canRouteInput(direct.token(), player));
    }

    @Test
    void takeoverPromotesTheCurrentOwnerAndRoutesOnlyTheCorrectPlayersInput() {
        Harness harness = new Harness();
        UUID citizen = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        var ambient = harness.reserveAmbient(citizen, ConversationKind.URGENT_CONTACT);
        harness.participation.providerReady(ambient.token());

        assertEquals(AiStatus.NONE, harness.presentation(ambient.token()));
        assertFalse(harness.participation.canRouteInput(ambient.token(), player));

        assertTrue(harness.registry.promoteToPlayer(ambient.token(), player, ConversationKind.PLAYER).isPresent());
        assertEquals(AiStatus.LISTENING, harness.presentation(ambient.token()));
        assertTrue(harness.participation.canRouteInput(ambient.token(), player));
        assertFalse(harness.participation.canRouteInput(ambient.token(), otherPlayer));

        harness.participation.playbackTalking(ambient.token());
        assertEquals(AiStatus.TALKING, harness.presentation(ambient.token()));
        assertTrue(harness.participation.canRouteInput(ambient.token(), player));
    }

    @Test
    void delayedCompletionOrStatusFromOldOwnerCannotOverwriteReplacement() {
        Harness harness = new Harness();
        UUID citizen = UUID.randomUUID();
        UUID oldPlayer = UUID.randomUUID();
        var oldOwner = harness.reservePlayer(citizen, oldPlayer);
        harness.participation.providerReady(oldOwner.token());
        assertEquals(AiStatus.LISTENING, harness.presentation(oldOwner.token()));

        assertTrue(harness.registry.end(
                oldOwner.token(), ForegroundSessionRegistry.TerminalReason.REPLACED, "replacement starts"));
        UUID replacementPlayer = UUID.randomUUID();
        var replacement = harness.reservePlayer(citizen, replacementPlayer);
        harness.participation.providerReady(replacement.token());
        assertEquals(AiStatus.LISTENING, harness.presentation(replacement.token()));

        assertFalse(harness.participation.canClearAfterCompletion(oldOwner.token()));
        harness.participation.playbackTalking(oldOwner.token());
        harness.participation.providerReady(oldOwner.token());

        assertTrue(harness.participation.presentationIfCurrent(oldOwner.token()).isEmpty());
        assertEquals(AiStatus.LISTENING, harness.presentation(replacement.token()));
        assertTrue(harness.participation.canRouteInput(replacement.token(), replacementPlayer));
        assertFalse(harness.participation.canRouteInput(replacement.token(), oldPlayer));
    }

    @Test
    void completionAndDisconnectBothRemoveListeningPromise() {
        Harness harness = new Harness();
        UUID completedPlayer = UUID.randomUUID();
        var completed = harness.reservePlayer(UUID.randomUUID(), completedPlayer);
        harness.participation.providerReady(completed.token());
        assertEquals(AiStatus.LISTENING, harness.presentation(completed.token()));

        assertTrue(harness.registry.end(
                completed.token(), ForegroundSessionRegistry.TerminalReason.COMPLETED, "done"));
        assertTrue(harness.participation.presentationIfCurrent(completed.token()).isEmpty());
        assertFalse(harness.participation.canRouteInput(completed.token(), completedPlayer));
        assertTrue(harness.participation.canClearAfterCompletion(completed.token()));

        UUID disconnectedPlayer = UUID.randomUUID();
        var disconnected = harness.reservePlayer(UUID.randomUUID(), disconnectedPlayer);
        harness.participation.providerReady(disconnected.token());
        assertEquals(AiStatus.LISTENING, harness.presentation(disconnected.token()));

        assertTrue(harness.registry.end(
                disconnected.token(), ForegroundSessionRegistry.TerminalReason.PLAYER_DISCONNECTED, "player left"));
        assertTrue(harness.participation.presentationIfCurrent(disconnected.token()).isEmpty());
        assertFalse(harness.participation.canRouteInput(disconnected.token(), disconnectedPlayer));
        assertTrue(harness.participation.canClearAfterCompletion(disconnected.token()));
    }


    @Test
    void closedMicrophoneTurnShowsBoundedThinkingThenReturnsToListening() {
        Harness harness = new Harness();
        UUID player = UUID.randomUUID();
        var direct = harness.reservePlayer(UUID.randomUUID(), player);
        harness.participation.providerReady(direct.token());
        assertEquals(AiStatus.LISTENING, harness.presentation(direct.token()));

        harness.participation.inputAwaitingResponse(direct.token(), true);
        assertEquals(AiStatus.THINKING, harness.presentation(direct.token()));
        assertTrue(harness.participation.canRouteInput(direct.token(), player));

        harness.participation.inputAwaitingResponse(direct.token(), false);
        assertEquals(AiStatus.LISTENING, harness.presentation(direct.token()));
    }

    private static final class Harness {
        final ForegroundSessionRegistry<String, FakeClient> registry;
        final DefaultConversationParticipationModule participation;

        Harness() {
            AtomicReference<DefaultConversationParticipationModule> module = new AtomicReference<>();
            registry = new ForegroundSessionRegistry<>(
                    new AtomicInteger(4)::get,
                    new AtomicLong()::get,
                    FakeClient::close,
                    client -> client.closed,
                    ended -> {
                        DefaultConversationParticipationModule current = module.get();
                        if (current != null) current.complete(ended.snapshot().token());
                    }
            );
            participation = new DefaultConversationParticipationModule(citizenId ->
                    registry.snapshot(citizenId)
                            .map(snapshot -> new ConversationParticipationModule.Ownership(
                                    snapshot.token(), snapshot.playerId()))
                            .orElse(null));
            module.set(participation);
        }

        ForegroundSessionRegistry.Reservation<String> reserveAmbient(UUID citizen, ConversationKind kind) {
            var reservation = registry.reserve(
                    citizen, "citizen", kind, ForegroundSessionRegistry.Priority.AMBIENT, null);
            assertTrue(reservation.granted());
            participation.register(reservation.token());
            return reservation;
        }

        ForegroundSessionRegistry.Reservation<String> reservePlayer(UUID citizen, UUID player) {
            var reservation = registry.reserve(
                    citizen, "citizen", ConversationKind.PLAYER, ForegroundSessionRegistry.Priority.PLAYER, player);
            assertTrue(reservation.granted());
            participation.register(reservation.token());
            return reservation;
        }

        AiStatus presentation(ForegroundSessionRegistry.Token token) {
            return participation.presentationIfCurrent(token).orElseThrow();
        }
    }

    private static final class FakeClient {
        boolean closed;

        void close() {
            closed = true;
        }
    }
}
