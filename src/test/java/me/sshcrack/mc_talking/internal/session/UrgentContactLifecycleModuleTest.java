package me.sshcrack.mc_talking.internal.session;

import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.network.AiStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrgentContactLifecycleModuleTest {
    private static final long WALK_TIMEOUT = TimeUnit.SECONDS.toNanos(60);
    private static final long REPATH_INTERVAL = TimeUnit.SECONDS.toNanos(1);

    @Test
    void walkAnnouncementAndSuccessfulCompletionKeepOneOwnerAndNeverAdvertiseListening() {
        Harness harness = new Harness();
        UUID citizen = harness.citizen();
        UUID origin = harness.player();

        var started = harness.startWalk(citizen, origin);
        assertTrue(started.started());
        var walkingToken = harness.token(citizen);
        assertEquals(AiStatus.URGENT_WALKING, harness.presentation(citizen));
        assertTrue(harness.adapter.navigationActive.contains(citizen));
        assertEquals(1, harness.registry.usedSlots());

        harness.adapter.arrived.add(citizen);
        harness.module.tick(harness.adapter);

        assertEquals(UrgentContactLifecycleModule.Phase.ANNOUNCING,
                harness.module.snapshot(citizen).orElseThrow().phase());
        assertEquals(walkingToken, harness.token(citizen), "arrival must retain exact foreground ownership");
        assertEquals(AiStatus.NONE, harness.presentation(citizen),
                "one-sided provider readiness must not advertise microphone listening");
        assertFalse(harness.adapter.navigationActive.contains(citizen));
        assertEquals(1, harness.adapter.announcementStarts.get());

        harness.adapter.talking(started.contactId());
        assertEquals(AiStatus.TALKING, harness.presentation(citizen));
        harness.adapter.idle(started.contactId());
        assertEquals(AiStatus.NONE, harness.presentation(citizen));

        harness.adapter.complete(started.contactId(), UrgentContactLifecycleModule.AnnouncementResult.completed());

        assertFalse(harness.module.isActive(citizen));
        assertEquals(0, harness.registry.usedSlots());
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.COMPLETED,
                harness.terminal(citizen).outcome());
        assertEquals(ForegroundSessionRegistry.TerminalReason.COMPLETED,
                harness.registry.lastTerminal(citizen).orElseThrow().reason());
        assertEquals(1, harness.completions(citizen).size());
        assertTrue(harness.participation.presentationIfCurrent(walkingToken).isEmpty());
    }

    @Test
    void arrivalWithFailedAnnouncementStartupReleasesWalkOwnershipAndStatus() {
        Harness harness = new Harness();
        UUID citizen = harness.citizen();
        UUID origin = harness.player();
        var started = harness.startWalk(citizen, origin);
        var token = harness.token(citizen);

        harness.adapter.announcementStartSucceeds = false;
        harness.adapter.arrived.add(citizen);
        harness.module.tick(harness.adapter);

        assertFalse(harness.module.isActive(citizen));
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.ANNOUNCEMENT_START_FAILED,
                harness.terminal(citizen).outcome());
        assertEquals(ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                harness.registry.lastTerminal(citizen).orElseThrow().reason());
        assertEquals(0, harness.registry.usedSlots());
        assertFalse(harness.adapter.navigationActive.contains(citizen));
        assertTrue(harness.participation.presentationIfCurrent(token).isEmpty());
        assertEquals(1, harness.completions(citizen).size());
        assertNotNull(started.contactId());
    }

    @Test
    void needResolutionCancelsWalkingAndActiveAnnouncementWithoutTouchingAnyPlayerConversation() {
        Harness harness = new Harness();
        UUID walkingCitizen = harness.citizen();
        UUID walkingOrigin = harness.player();
        harness.startWalk(walkingCitizen, walkingOrigin);

        harness.adapter.urgentNeed.remove(walkingCitizen);
        harness.module.tick(harness.adapter);
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.NEED_RESOLVED,
                harness.terminal(walkingCitizen).outcome());
        assertEquals(ForegroundSessionRegistry.TerminalReason.CANCELLED,
                harness.registry.lastTerminal(walkingCitizen).orElseThrow().reason());

        UUID announcingCitizen = harness.citizen();
        UUID announcingOrigin = harness.player();
        var announcing = harness.startWalk(announcingCitizen, announcingOrigin);
        harness.adapter.arrived.add(announcingCitizen);
        harness.module.tick(harness.adapter);
        assertEquals(UrgentContactLifecycleModule.Phase.ANNOUNCING,
                harness.module.snapshot(announcingCitizen).orElseThrow().phase());

        harness.adapter.urgentNeed.remove(announcingCitizen);
        harness.module.tick(harness.adapter);
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.NEED_RESOLVED,
                harness.terminal(announcingCitizen).outcome());
        assertEquals(ForegroundSessionRegistry.TerminalReason.CANCELLED,
                harness.registry.lastTerminal(announcingCitizen).orElseThrow().reason());
        assertEquals(1, harness.completions(announcingCitizen).size());

        // Provider close/completion after lifecycle cancellation is stale and cannot deliver twice.
        harness.adapter.complete(announcing.contactId(), UrgentContactLifecycleModule.AnnouncementResult.completed());
        assertEquals(1, harness.completions(announcingCitizen).size());
        assertEquals(0, harness.registry.usedSlots());
    }

    @Test
    void playerDepartureCitizenInvalidationAndTargetContextLossTerminateOwnedContact() {
        Harness harness = new Harness();

        UUID departedCitizen = harness.citizen();
        UUID departedPlayer = harness.player();
        harness.startWalk(departedCitizen, departedPlayer);
        harness.module.onPlayerDeparture(departedPlayer, harness.adapter);
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.PLAYER_DEPARTED,
                harness.terminal(departedCitizen).outcome());
        assertEquals(ForegroundSessionRegistry.TerminalReason.PLAYER_DISCONNECTED,
                harness.registry.lastTerminal(departedCitizen).orElseThrow().reason());

        UUID invalidCitizen = harness.citizen();
        UUID invalidOrigin = harness.player();
        harness.startWalk(invalidCitizen, invalidOrigin);
        harness.adapter.validCitizens.remove(invalidCitizen);
        harness.module.tick(harness.adapter);
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.CITIZEN_INVALID,
                harness.terminal(invalidCitizen).outcome());
        assertEquals(ForegroundSessionRegistry.TerminalReason.ENTITY_UNAVAILABLE,
                harness.registry.lastTerminal(invalidCitizen).orElseThrow().reason());

        UUID contextCitizen = harness.citizen();
        UUID contextOrigin = harness.player();
        harness.startWalk(contextCitizen, contextOrigin);
        harness.adapter.invalidTargetContexts.add(contextCitizen);
        harness.module.tick(harness.adapter);
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.TARGET_CONTEXT_LOST,
                harness.terminal(contextCitizen).outcome());
        assertEquals(0, harness.registry.usedSlots());
    }

    @Test
    void takeoverDuringWalkingReplacesWalkOwnerButLifecycleNeverEndsReplacement() {
        Harness harness = new Harness();
        UUID citizen = harness.citizen();
        UUID origin = harness.player();
        var urgent = harness.startWalk(citizen, origin);
        var oldToken = harness.token(citizen);
        UUID takeoverPlayer = harness.player();

        var directToken = harness.adapter.replaceWithDirect(citizen, takeoverPlayer);
        assertNotEquals(oldToken, directToken);
        assertTrue(harness.module.onPlayerTakeover(citizen, takeoverPlayer, harness.adapter));

        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.TAKEN_OVER,
                harness.terminal(citizen).outcome());
        assertEquals(directToken, harness.token(citizen));
        assertEquals(takeoverPlayer, harness.registry.playerForCitizen(citizen));
        assertEquals(AiStatus.LISTENING, harness.presentation(citizen));
        assertFalse(harness.adapter.navigationActive.contains(citizen));
        assertEquals(1, harness.completions(citizen).size());
        assertNotNull(urgent.contactId());
    }

    @Test
    void takeoverDuringAnnouncementPromotesSameOwnerAndOriginalNeedNoLongerControlsDirectConversation() {
        Harness harness = new Harness();
        UUID citizen = harness.citizen();
        UUID origin = harness.player();
        var urgent = harness.startWalk(citizen, origin);
        harness.adapter.arrived.add(citizen);
        harness.module.tick(harness.adapter);
        var ambientToken = harness.token(citizen);
        UUID takeoverPlayer = harness.player();

        harness.adapter.promoteToDirect(citizen, takeoverPlayer);
        assertTrue(harness.module.onPlayerTakeover(citizen, takeoverPlayer, harness.adapter));

        assertEquals(ambientToken, harness.token(citizen), "announcement takeover should retain provider token");
        assertEquals(AiStatus.LISTENING, harness.presentation(citizen));
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.TAKEN_OVER,
                harness.terminal(citizen).outcome());

        harness.adapter.urgentNeed.remove(citizen);
        harness.module.tick(harness.adapter);
        assertEquals(ambientToken, harness.token(citizen),
                "need resolution after handoff must not end the normal player conversation");
        assertEquals(takeoverPlayer, harness.registry.playerForCitizen(citizen));

        harness.adapter.complete(urgent.contactId(), UrgentContactLifecycleModule.AnnouncementResult.completed());
        assertEquals(ambientToken, harness.token(citizen), "stale announcement completion must not end promoted direct owner");
        assertEquals(1, harness.completions(citizen).size());
    }

    @Test
    void takeoverRacingCompletionOrCancellationHasOneWinnerAndLeavesReplacementOwned() {
        Harness takeoverFirst = new Harness();
        UUID citizen = takeoverFirst.citizen();
        UUID origin = takeoverFirst.player();
        var urgent = takeoverFirst.startWalk(citizen, origin);
        takeoverFirst.adapter.arrived.add(citizen);
        takeoverFirst.module.tick(takeoverFirst.adapter);
        UUID player = takeoverFirst.player();
        takeoverFirst.adapter.promoteToDirect(citizen, player);
        takeoverFirst.module.onPlayerTakeover(citizen, player, takeoverFirst.adapter);
        var promotedToken = takeoverFirst.token(citizen);
        takeoverFirst.adapter.complete(urgent.contactId(), UrgentContactLifecycleModule.AnnouncementResult.completed());
        assertEquals(promotedToken, takeoverFirst.token(citizen));
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.TAKEN_OVER,
                takeoverFirst.terminal(citizen).outcome());
        assertEquals(1, takeoverFirst.completions(citizen).size());

        Harness completionFirst = new Harness();
        UUID completedCitizen = completionFirst.citizen();
        UUID completedOrigin = completionFirst.player();
        var completing = completionFirst.startWalk(completedCitizen, completedOrigin);
        completionFirst.adapter.arrived.add(completedCitizen);
        completionFirst.module.tick(completionFirst.adapter);
        completionFirst.adapter.complete(completing.contactId(), UrgentContactLifecycleModule.AnnouncementResult.completed());
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.COMPLETED,
                completionFirst.terminal(completedCitizen).outcome());
        UUID replacementPlayer = completionFirst.player();
        var replacement = completionFirst.adapter.reserveDirect(completedCitizen, replacementPlayer);
        assertFalse(completionFirst.module.onPlayerTakeover(completedCitizen, replacementPlayer, completionFirst.adapter));
        assertEquals(replacement, completionFirst.token(completedCitizen));

        Harness cancellationFirst = new Harness();
        UUID cancelledCitizen = cancellationFirst.citizen();
        UUID cancelledOrigin = cancellationFirst.player();
        var cancelling = cancellationFirst.startWalk(cancelledCitizen, cancelledOrigin);
        cancellationFirst.adapter.arrived.add(cancelledCitizen);
        cancellationFirst.module.tick(cancellationFirst.adapter);
        assertTrue(cancellationFirst.module.cancel(cancelledCitizen, cancellationFirst.adapter, "manual cancellation"));
        UUID cancelledReplacementPlayer = cancellationFirst.player();
        var cancelledReplacement = cancellationFirst.adapter.reserveDirect(cancelledCitizen, cancelledReplacementPlayer);
        cancellationFirst.adapter.complete(cancelling.contactId(), UrgentContactLifecycleModule.AnnouncementResult.completed());
        assertEquals(cancelledReplacement, cancellationFirst.token(cancelledCitizen));
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.CANCELLED,
                cancellationFirst.terminal(cancelledCitizen).outcome());
    }

    @Test
    void oldCallbacksAfterReplacementCannotTerminateOrRepaintNewUrgentContact() {
        Harness harness = new Harness();
        UUID citizen = harness.citizen();
        UUID origin = harness.player();
        var old = harness.startWalk(citizen, origin);
        harness.adapter.arrived.add(citizen);
        harness.module.tick(harness.adapter);
        var oldToken = harness.token(citizen);
        assertTrue(harness.module.cancel(citizen, harness.adapter, "replace old contact"));

        harness.adapter.arrived.remove(citizen);
        var replacement = harness.startWalk(citizen, origin);
        var replacementToken = harness.token(citizen);
        assertNotEquals(oldToken, replacementToken);
        assertEquals(AiStatus.URGENT_WALKING, harness.presentation(citizen));

        harness.adapter.complete(old.contactId(), UrgentContactLifecycleModule.AnnouncementResult.completed());
        assertEquals(replacementToken, harness.token(citizen));
        assertEquals(AiStatus.URGENT_WALKING, harness.presentation(citizen));
        assertTrue(harness.module.isActive(citizen));
        assertEquals(replacement.contactId(), harness.module.snapshot(citizen).orElseThrow().contactId());
    }

    @Test
    void repeatedCancellationAndCleanupAreIdempotentAndCompletionIsDeliveredOnce() {
        Harness harness = new Harness();
        UUID citizen = harness.citizen();
        UUID origin = harness.player();
        var started = harness.startWalk(citizen, origin);

        assertTrue(harness.module.cancel(citizen, harness.adapter, "first"));
        assertFalse(harness.module.cancel(citizen, harness.adapter, "second"));
        harness.module.tick(harness.adapter);
        harness.adapter.complete(started.contactId(), UrgentContactLifecycleModule.AnnouncementResult.failed("late"));
        harness.module.shutdown(harness.adapter);

        assertEquals(1, harness.completions(citizen).size());
        assertEquals(1, harness.adapter.reservationEndCalls.getOrDefault(citizen, 0));
        assertEquals(0, harness.registry.usedSlots());
        assertFalse(harness.adapter.navigationActive.contains(citizen));
    }

    @Test
    void capacityCooldownAndArrivalFailurePreserveDeliberateRetrySemantics() {
        Harness harness = new Harness();
        harness.capacity.set(1);
        UUID player = harness.player();
        UUID first = harness.citizen();
        UUID second = harness.citizen();

        var started = harness.startWalk(first, player);
        assertTrue(started.started());
        var blocked = harness.module.startWalking(second, player, harness.adapter, harness.completionFor(second));
        assertEquals(UrgentContactLifecycleModule.StartStatus.RESERVATION_UNAVAILABLE, blocked.status());
        assertFalse(blocked.started());

        long cooldown = TimeUnit.SECONDS.toNanos(60);
        harness.module.recordPlayerContact(player);
        assertTrue(harness.module.isPlayerOnCooldown(player, cooldown));
        harness.now.addAndGet(cooldown - 1);
        assertTrue(harness.module.isPlayerOnCooldown(player, cooldown));
        harness.now.incrementAndGet();
        assertFalse(harness.module.isPlayerOnCooldown(player, cooldown));

        // A walk was accepted already. If its later announcement startup fails, ownership is freed
        // but the automatic-contact cooldown timestamp remains until expiry/departure.
        harness.now.set(0);
        harness.module.recordPlayerContact(player);
        harness.adapter.announcementStartSucceeds = false;
        harness.adapter.arrived.add(first);
        harness.module.tick(harness.adapter);
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.ANNOUNCEMENT_START_FAILED,
                harness.terminal(first).outcome());
        assertTrue(harness.module.isPlayerOnCooldown(player, cooldown));
        assertEquals(0, harness.registry.usedSlots());

        harness.module.onPlayerDeparture(player, harness.adapter);
        assertFalse(harness.module.isPlayerOnCooldown(player, cooldown),
                "departure preserves prior behavior by clearing per-player urgent cooldown");

        // Immediate announcement startup failure is not a successful start, matching the old
        // no-walk path where the caller does not consume the per-player cooldown.
        UUID immediate = harness.citizen();
        UUID immediatePlayer = harness.player();
        var failedImmediate = harness.module.startAnnouncement(
                immediate, immediatePlayer, harness.adapter, harness.completionFor(immediate));
        assertEquals(UrgentContactLifecycleModule.StartStatus.ANNOUNCEMENT_START_FAILED, failedImmediate.status());
        assertFalse(failedImmediate.started());
        assertFalse(harness.module.isPlayerOnCooldown(immediatePlayer, cooldown));
    }

    @Test
    void ownershipLossAndWalkTimeoutAreDefinedTerminalOutcomes() {
        Harness harness = new Harness();
        UUID citizen = harness.citizen();
        UUID origin = harness.player();
        harness.startWalk(citizen, origin);
        var old = harness.token(citizen);
        assertTrue(harness.registry.end(old, ForegroundSessionRegistry.TerminalReason.EVICTED, "capacity preemption"));
        harness.module.tick(harness.adapter);
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.OWNERSHIP_LOST,
                harness.terminal(citizen).outcome());

        UUID timedOut = harness.citizen();
        UUID timedOutOrigin = harness.player();
        harness.startWalk(timedOut, timedOutOrigin);
        harness.now.addAndGet(WALK_TIMEOUT);
        harness.module.tick(harness.adapter);
        assertEquals(UrgentContactLifecycleModule.TerminalOutcome.WALK_TIMEOUT,
                harness.terminal(timedOut).outcome());
        assertFalse(harness.adapter.navigationActive.contains(timedOut));
    }

    private static final class Harness {
        final AtomicLong now = new AtomicLong();
        final AtomicInteger capacity = new AtomicInteger(8);
        final ForegroundSessionRegistry<String, FakeClient> registry;
        final DefaultConversationParticipationModule participation;
        final FakeAdapter adapter;
        final UrgentContactLifecycleModule module;
        final Map<UUID, List<UrgentContactLifecycleModule.TerminalContact>> completions = new HashMap<>();

        Harness() {
            AtomicReference<DefaultConversationParticipationModule> moduleRef = new AtomicReference<>();
            registry = new ForegroundSessionRegistry<>(
                    capacity::get,
                    now::get,
                    FakeClient::close,
                    client -> client.closed,
                    ended -> {
                        DefaultConversationParticipationModule current = moduleRef.get();
                        if (current != null) current.complete(ended.snapshot().token());
                    }
            );
            participation = new DefaultConversationParticipationModule(citizenId ->
                    registry.snapshot(citizenId)
                            .map(snapshot -> new ConversationParticipationModule.Ownership(
                                    snapshot.token(), snapshot.playerId()))
                            .orElse(null));
            moduleRef.set(participation);
            adapter = new FakeAdapter(registry, participation);
            module = new UrgentContactLifecycleModule(
                    now::get,
                    new UrgentContactLifecycleModule.Timing(WALK_TIMEOUT, REPATH_INTERVAL));
        }

        UUID citizen() {
            UUID id = UUID.randomUUID();
            adapter.validCitizens.add(id);
            adapter.urgentNeed.add(id);
            return id;
        }

        UUID player() {
            UUID id = UUID.randomUUID();
            adapter.validPlayers.add(id);
            return id;
        }

        UrgentContactLifecycleModule.StartResult startWalk(UUID citizen, UUID player) {
            return module.startWalking(citizen, player, adapter, completionFor(citizen));
        }

        Consumer<UrgentContactLifecycleModule.TerminalContact> completionFor(UUID citizen) {
            return terminal -> completions.computeIfAbsent(citizen, ignored -> new ArrayList<>()).add(terminal);
        }

        List<UrgentContactLifecycleModule.TerminalContact> completions(UUID citizen) {
            return completions.getOrDefault(citizen, List.of());
        }

        UrgentContactLifecycleModule.TerminalContact terminal(UUID citizen) {
            List<UrgentContactLifecycleModule.TerminalContact> terminalEvents = completions(citizen);
            assertEquals(1, terminalEvents.size(), "urgent lifecycle completion must be delivered exactly once");
            return terminalEvents.get(0);
        }

        ForegroundSessionRegistry.Token token(UUID citizen) {
            return registry.token(citizen);
        }

        AiStatus presentation(UUID citizen) {
            var token = token(citizen);
            assertNotNull(token);
            return participation.presentationIfCurrent(token).orElseThrow();
        }
    }

    private static final class FakeAdapter implements UrgentContactLifecycleModule.Adapter {
        private final ForegroundSessionRegistry<String, FakeClient> registry;
        private final DefaultConversationParticipationModule participation;
        final Set<UUID> validPlayers = new HashSet<>();
        final Set<UUID> validCitizens = new HashSet<>();
        final Set<UUID> invalidTargetContexts = new HashSet<>();
        final Set<UUID> urgentNeed = new HashSet<>();
        final Set<UUID> arrived = new HashSet<>();
        final Set<UUID> navigationActive = new HashSet<>();
        final Map<UUID, Integer> navigationMoves = new HashMap<>();
        final Map<UUID, Integer> reservationEndCalls = new HashMap<>();
        final Map<UUID, PendingAnnouncement> announcements = new HashMap<>();
        final AtomicInteger announcementStarts = new AtomicInteger();
        boolean announcementStartSucceeds = true;

        FakeAdapter(
                ForegroundSessionRegistry<String, FakeClient> registry,
                DefaultConversationParticipationModule participation
        ) {
            this.registry = registry;
            this.participation = participation;
        }

        @Override
        public UrgentContactLifecycleModule.Reservation reserve(UUID citizenId) {
            var reservation = registry.reserve(
                    citizenId,
                    citizenId.toString(),
                    ConversationKind.URGENT_CONTACT,
                    ForegroundSessionRegistry.Priority.AMBIENT,
                    null
            );
            if (!reservation.granted()) return null;
            participation.register(reservation.token());
            return new FakeReservation(citizenId, reservation.token());
        }

        @Override
        public boolean playerValid(UUID playerId) {
            return validPlayers.contains(playerId);
        }

        @Override
        public boolean citizenValid(UUID citizenId) {
            return validCitizens.contains(citizenId);
        }

        @Override
        public boolean targetContextValid(UUID citizenId, UUID playerId) {
            return validCitizens.contains(citizenId)
                    && validPlayers.contains(playerId)
                    && !invalidTargetContexts.contains(citizenId);
        }

        @Override
        public boolean urgentNeedPresent(UUID citizenId) {
            return urgentNeed.contains(citizenId);
        }

        @Override
        public boolean arrived(UUID citizenId, UUID playerId) {
            return arrived.contains(citizenId);
        }

        @Override
        public UUID directPlayer(UUID citizenId) {
            return registry.playerForCitizen(citizenId);
        }

        @Override
        public void navigateTo(UUID citizenId, UUID playerId) {
            navigationActive.add(citizenId);
            navigationMoves.merge(citizenId, 1, Integer::sum);
        }

        @Override
        public void stopNavigation(UUID citizenId) {
            navigationActive.remove(citizenId);
        }

        @Override
        public boolean startAnnouncement(
                UrgentContactLifecycleModule.Snapshot contact,
                UrgentContactLifecycleModule.Reservation reservation,
                Consumer<UrgentContactLifecycleModule.AnnouncementResult> completion
        ) {
            announcementStarts.incrementAndGet();
            if (!announcementStartSucceeds) return false;
            FakeReservation fake = (FakeReservation) reservation;
            assertTrue(registry.attachClient(fake.token, new FakeClient()));
            assertTrue(registry.markActive(fake.token, "announcement active"));
            participation.providerConnecting(fake.token);
            participation.providerReady(fake.token);
            announcements.put(contact.contactId(), new PendingAnnouncement(contact.citizenId(), fake.token, completion));
            return true;
        }

        void talking(UUID contactId) {
            PendingAnnouncement pending = announcements.get(contactId);
            assertNotNull(pending);
            participation.playbackTalking(pending.token);
        }

        void idle(UUID contactId) {
            PendingAnnouncement pending = announcements.get(contactId);
            assertNotNull(pending);
            participation.playbackIdle(pending.token);
        }

        void complete(UUID contactId, UrgentContactLifecycleModule.AnnouncementResult result) {
            PendingAnnouncement pending = announcements.get(contactId);
            if (pending != null) pending.completion.accept(result);
        }

        ForegroundSessionRegistry.Token replaceWithDirect(UUID citizenId, UUID playerId) {
            var old = registry.token(citizenId);
            assertNotNull(old);
            assertTrue(registry.end(old, ForegroundSessionRegistry.TerminalReason.REPLACED, "direct takeover"));
            return reserveDirect(citizenId, playerId);
        }

        ForegroundSessionRegistry.Token reserveDirect(UUID citizenId, UUID playerId) {
            var direct = registry.reserve(
                    citizenId,
                    citizenId.toString(),
                    ConversationKind.PLAYER,
                    ForegroundSessionRegistry.Priority.PLAYER,
                    playerId
            );
            assertTrue(direct.granted());
            participation.register(direct.token());
            assertTrue(registry.attachClient(direct.token(), new FakeClient()));
            assertTrue(registry.markActive(direct.token(), "direct active"));
            participation.providerReady(direct.token());
            return direct.token();
        }

        void promoteToDirect(UUID citizenId, UUID playerId) {
            var token = registry.token(citizenId);
            assertNotNull(token);
            assertTrue(registry.promoteToPlayer(token, playerId, ConversationKind.PLAYER).isPresent());
        }

        private final class FakeReservation implements UrgentContactLifecycleModule.Reservation {
            final UUID citizenId;
            final ForegroundSessionRegistry.Token token;

            private FakeReservation(UUID citizenId, ForegroundSessionRegistry.Token token) {
                this.citizenId = citizenId;
                this.token = token;
            }

            @Override
            public boolean isCurrent() {
                return registry.isCurrent(token);
            }

            @Override
            public void urgentWalking(boolean active) {
                participation.urgentWalking(token, active);
            }

            @Override
            public boolean end(ForegroundSessionRegistry.TerminalReason reason, String detail) {
                reservationEndCalls.merge(citizenId, 1, Integer::sum);
                return registry.end(token, reason, detail);
            }
        }
    }

    private record PendingAnnouncement(
            UUID citizenId,
            ForegroundSessionRegistry.Token token,
            Consumer<UrgentContactLifecycleModule.AnnouncementResult> completion
    ) {
    }

    private static final class FakeClient {
        boolean closed;

        void close() {
            closed = true;
        }
    }
}
