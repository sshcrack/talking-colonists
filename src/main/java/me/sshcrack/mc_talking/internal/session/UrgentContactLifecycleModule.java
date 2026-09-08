package me.sshcrack.mc_talking.internal.session;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Owns the complete lifecycle of citizen-initiated urgent contact.
 *
 * <p>A contact has one immutable origin player and one token-owned foreground reservation. Walking
 * and the one-sided announcement are phases of the same contact; current direct-player
 * participation is deliberately queried separately and is never inferred from that origin. A
 * successful player takeover relinquishes the contact without ending the reservation, because an
 * announcing ambient reservation may already have been promoted in-place to the direct player
 * conversation.</p>
 *
 * <p>All provider callbacks carry the contact id. Terminal removal happens before cleanup side
 * effects, so repeated cancellation, synchronous close callbacks, and callbacks from replaced
 * contacts are idempotent and cannot terminate a successor.</p>
 */
public final class UrgentContactLifecycleModule {
    public enum Phase {
        WALKING,
        ANNOUNCING
    }

    public enum StartStatus {
        STARTED,
        ALREADY_ACTIVE,
        RESERVATION_UNAVAILABLE,
        ANNOUNCEMENT_START_FAILED
    }

    public enum TerminalOutcome {
        COMPLETED,
        ANNOUNCEMENT_START_FAILED,
        ANNOUNCEMENT_FAILED,
        ANNOUNCEMENT_CANCELLED,
        NEED_RESOLVED,
        PLAYER_DEPARTED,
        CITIZEN_INVALID,
        TARGET_CONTEXT_LOST,
        WALK_TIMEOUT,
        TAKEN_OVER,
        OWNERSHIP_LOST,
        CANCELLED,
        SERVER_STOPPED
    }

    public enum AnnouncementStatus {
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public record AnnouncementResult(AnnouncementStatus status, String detail) {
        public AnnouncementResult {
            Objects.requireNonNull(status, "status");
            detail = detail == null || detail.isBlank() ? status.name().toLowerCase() : detail;
        }

        public static AnnouncementResult completed() {
            return new AnnouncementResult(AnnouncementStatus.COMPLETED, "announcement completed");
        }

        public static AnnouncementResult failed(String detail) {
            return new AnnouncementResult(AnnouncementStatus.FAILED, detail);
        }

        public static AnnouncementResult cancelled(String detail) {
            return new AnnouncementResult(AnnouncementStatus.CANCELLED, detail);
        }
    }

    public record Timing(long walkTimeoutNanos, long repathIntervalNanos) {
        public Timing {
            if (walkTimeoutNanos <= 0) throw new IllegalArgumentException("walkTimeoutNanos must be positive");
            if (repathIntervalNanos <= 0) throw new IllegalArgumentException("repathIntervalNanos must be positive");
        }
    }

    public record StartResult(StartStatus status, @Nullable UUID contactId) {
        public boolean started() {
            return status == StartStatus.STARTED;
        }
    }

    public record Snapshot(
            UUID contactId,
            UUID citizenId,
            UUID originPlayerId,
            Phase phase,
            long startedAtNanos,
            long lastRepathAtNanos
    ) {
    }

    public record TerminalContact(
            UUID contactId,
            UUID citizenId,
            UUID originPlayerId,
            TerminalOutcome outcome,
            String detail
    ) {
    }

    /** Exact ownership handle supplied by the foreground registry adapter. */
    public interface Reservation {
        boolean isCurrent();

        void urgentWalking(boolean active);

        boolean end(ForegroundSessionRegistry.TerminalReason reason, String detail);
    }

    /** Minecraft/world/provider seam. Tests use deterministic adapters with no game runtime. */
    public interface Adapter {
        @Nullable Reservation reserve(UUID citizenId);

        boolean playerValid(UUID playerId);

        boolean citizenValid(UUID citizenId);

        /** True while citizen and origin player still form a meaningful same-world target context. */
        boolean targetContextValid(UUID citizenId, UUID playerId);

        boolean urgentNeedPresent(UUID citizenId);

        boolean arrived(UUID citizenId, UUID playerId);

        /** Current direct participant, independent from the contact's immutable origin player. */
        @Nullable UUID directPlayer(UUID citizenId);

        void navigateTo(UUID citizenId, UUID playerId);

        void stopNavigation(UUID citizenId);

        /**
         * Starts the one-sided announcement on the supplied existing reservation. The callback may
         * run synchronously; callers must therefore treat the contact id as the ownership guard.
         */
        boolean startAnnouncement(
                Snapshot contact,
                Reservation reservation,
                Consumer<AnnouncementResult> completion
        );
    }

    private final LongSupplier nanoClock;
    private final Timing timing;
    private final Map<UUID, Contact> contactsByCitizen = new LinkedHashMap<>();
    private final Map<UUID, Long> lastPlayerContactAtNanos = new HashMap<>();

    public UrgentContactLifecycleModule(LongSupplier nanoClock, Timing timing) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.timing = Objects.requireNonNull(timing, "timing");
    }

    public synchronized StartResult startWalking(
            UUID citizenId,
            UUID originPlayerId,
            Adapter adapter,
            @Nullable Consumer<TerminalContact> completion
    ) {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(originPlayerId, "originPlayerId");
        Objects.requireNonNull(adapter, "adapter");
        if (contactsByCitizen.containsKey(citizenId)) {
            return new StartResult(StartStatus.ALREADY_ACTIVE, null);
        }

        Reservation reservation = adapter.reserve(citizenId);
        if (reservation == null) {
            return new StartResult(StartStatus.RESERVATION_UNAVAILABLE, null);
        }

        long now = nanoClock.getAsLong();
        Contact contact = new Contact(
                UUID.randomUUID(), citizenId, originPlayerId, Phase.WALKING,
                now, now, reservation, completion);
        contactsByCitizen.put(citizenId, contact);
        reservation.urgentWalking(true);
        adapter.navigateTo(citizenId, originPlayerId);
        return new StartResult(StartStatus.STARTED, contact.contactId);
    }

    public synchronized StartResult startAnnouncement(
            UUID citizenId,
            UUID originPlayerId,
            Adapter adapter,
            @Nullable Consumer<TerminalContact> completion
    ) {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(originPlayerId, "originPlayerId");
        Objects.requireNonNull(adapter, "adapter");
        if (contactsByCitizen.containsKey(citizenId)) {
            return new StartResult(StartStatus.ALREADY_ACTIVE, null);
        }

        Reservation reservation = adapter.reserve(citizenId);
        if (reservation == null) {
            return new StartResult(StartStatus.RESERVATION_UNAVAILABLE, null);
        }

        long now = nanoClock.getAsLong();
        Contact contact = new Contact(
                UUID.randomUUID(), citizenId, originPlayerId, Phase.ANNOUNCING,
                now, now, reservation, completion);
        contactsByCitizen.put(citizenId, contact);
        if (!startAnnouncementLocked(contact, adapter)) {
            finishLocked(contact, adapter, TerminalOutcome.ANNOUNCEMENT_START_FAILED,
                    ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                    "urgent announcement failed to start", true);
            return new StartResult(StartStatus.ANNOUNCEMENT_START_FAILED, contact.contactId);
        }
        return new StartResult(StartStatus.STARTED, contact.contactId);
    }

    /** Advances all owned contacts once. Intended for the Minecraft server thread. */
    public synchronized void tick(Adapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        for (UUID citizenId : new ArrayList<>(contactsByCitizen.keySet())) {
            Contact contact = contactsByCitizen.get(citizenId);
            if (contact == null) continue;
            tickContactLocked(contact, adapter);
        }
    }

    /** Explicit takeover notification used by the direct-conversation startup path. */
    public synchronized boolean onPlayerTakeover(UUID citizenId, UUID playerId, Adapter adapter) {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(adapter, "adapter");
        Contact contact = contactsByCitizen.get(citizenId);
        if (contact == null) return false;
        finishLocked(contact, adapter, TerminalOutcome.TAKEN_OVER, null,
                "urgent contact handed off to direct player " + playerId, false);
        return true;
    }

    /** Cancels every contact whose immutable journey origin is the departing player. */
    public synchronized void onPlayerDeparture(UUID playerId, Adapter adapter) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(adapter, "adapter");
        for (Contact contact : new ArrayList<>(contactsByCitizen.values())) {
            if (!contact.originPlayerId.equals(playerId)) continue;
            finishLocked(contact, adapter, TerminalOutcome.PLAYER_DEPARTED,
                    ForegroundSessionRegistry.TerminalReason.PLAYER_DISCONNECTED,
                    "urgent contact origin player departed", true);
        }
        // Preserve the previous behavior: reconnecting players do not inherit the old per-player
        // urgent-contact cooldown.
        lastPlayerContactAtNanos.remove(playerId);
    }

    public synchronized boolean cancel(UUID citizenId, Adapter adapter, String detail) {
        Objects.requireNonNull(citizenId, "citizenId");
        Objects.requireNonNull(adapter, "adapter");
        Contact contact = contactsByCitizen.get(citizenId);
        if (contact == null) return false;
        finishLocked(contact, adapter, TerminalOutcome.CANCELLED,
                ForegroundSessionRegistry.TerminalReason.CANCELLED,
                detail == null || detail.isBlank() ? "urgent contact cancelled" : detail, true);
        return true;
    }

    public synchronized void shutdown(Adapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        for (Contact contact : new ArrayList<>(contactsByCitizen.values())) {
            finishLocked(contact, adapter, TerminalOutcome.SERVER_STOPPED,
                    ForegroundSessionRegistry.TerminalReason.SERVER_SHUTDOWN,
                    "server stopping", true);
        }
        lastPlayerContactAtNanos.clear();
    }

    public synchronized boolean isActive(UUID citizenId) {
        return contactsByCitizen.containsKey(citizenId);
    }

    public synchronized Optional<Snapshot> snapshot(UUID citizenId) {
        Contact contact = contactsByCitizen.get(citizenId);
        return contact == null ? Optional.empty() : Optional.of(snapshotOf(contact));
    }

    public synchronized boolean isPlayerOnCooldown(UUID playerId, long cooldownNanos) {
        if (cooldownNanos <= 0) return false;
        Long last = lastPlayerContactAtNanos.get(playerId);
        return last != null && nanoClock.getAsLong() - last < cooldownNanos;
    }

    public synchronized void recordPlayerContact(UUID playerId) {
        lastPlayerContactAtNanos.put(Objects.requireNonNull(playerId, "playerId"), nanoClock.getAsLong());
    }

    private void tickContactLocked(Contact contact, Adapter adapter) {
        UUID directPlayer = adapter.directPlayer(contact.citizenId);
        if (directPlayer != null) {
            finishLocked(contact, adapter, TerminalOutcome.TAKEN_OVER, null,
                    "urgent contact observed direct player takeover by " + directPlayer, false);
            return;
        }
        if (!contact.reservation.isCurrent()) {
            finishLocked(contact, adapter, TerminalOutcome.OWNERSHIP_LOST, null,
                    "urgent contact foreground ownership was replaced", false);
            return;
        }
        if (!adapter.playerValid(contact.originPlayerId)) {
            finishLocked(contact, adapter, TerminalOutcome.PLAYER_DEPARTED,
                    ForegroundSessionRegistry.TerminalReason.PLAYER_DISCONNECTED,
                    "urgent contact origin player is unavailable", true);
            return;
        }
        if (!adapter.citizenValid(contact.citizenId)) {
            finishLocked(contact, adapter, TerminalOutcome.CITIZEN_INVALID,
                    ForegroundSessionRegistry.TerminalReason.ENTITY_UNAVAILABLE,
                    "urgent contact citizen is unavailable", true);
            return;
        }
        if (!adapter.targetContextValid(contact.citizenId, contact.originPlayerId)) {
            finishLocked(contact, adapter, TerminalOutcome.TARGET_CONTEXT_LOST,
                    ForegroundSessionRegistry.TerminalReason.CANCELLED,
                    "urgent contact target context is no longer valid", true);
            return;
        }
        if (!adapter.urgentNeedPresent(contact.citizenId)) {
            finishLocked(contact, adapter, TerminalOutcome.NEED_RESOLVED,
                    ForegroundSessionRegistry.TerminalReason.CANCELLED,
                    contact.phase == Phase.WALKING
                            ? "urgent need resolved before announcement"
                            : "urgent need resolved during announcement",
                    true);
            return;
        }

        if (contact.phase == Phase.ANNOUNCING) return;

        long now = nanoClock.getAsLong();
        if (now - contact.startedAtNanos >= timing.walkTimeoutNanos()) {
            finishLocked(contact, adapter, TerminalOutcome.WALK_TIMEOUT,
                    ForegroundSessionRegistry.TerminalReason.CANCELLED,
                    "urgent contact walk timed out", true);
            return;
        }

        if (adapter.arrived(contact.citizenId, contact.originPlayerId)) {
            adapter.stopNavigation(contact.citizenId);
            contact.reservation.urgentWalking(false);
            contact.phase = Phase.ANNOUNCING;
            if (!startAnnouncementLocked(contact, adapter)) {
                finishLocked(contact, adapter, TerminalOutcome.ANNOUNCEMENT_START_FAILED,
                        ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                        "urgent announcement failed to start after arrival", true);
            }
            return;
        }

        if (now - contact.lastRepathAtNanos >= timing.repathIntervalNanos()) {
            adapter.navigateTo(contact.citizenId, contact.originPlayerId);
            contact.lastRepathAtNanos = now;
        }
    }

    private boolean startAnnouncementLocked(Contact contact, Adapter adapter) {
        UUID expectedContactId = contact.contactId;
        return adapter.startAnnouncement(
                snapshotOf(contact),
                contact.reservation,
                result -> onAnnouncementCompletion(expectedContactId, result, adapter)
        );
    }

    private synchronized void onAnnouncementCompletion(
            UUID expectedContactId,
            AnnouncementResult result,
            Adapter adapter
    ) {
        Objects.requireNonNull(result, "result");
        Contact contact = contactByIdLocked(expectedContactId);
        if (contact == null || contact.phase != Phase.ANNOUNCING) return;

        switch (result.status()) {
            case COMPLETED -> finishLocked(contact, adapter, TerminalOutcome.COMPLETED,
                    ForegroundSessionRegistry.TerminalReason.COMPLETED, result.detail(), true);
            case FAILED -> finishLocked(contact, adapter, TerminalOutcome.ANNOUNCEMENT_FAILED,
                    ForegroundSessionRegistry.TerminalReason.PROVIDER_FAILURE, result.detail(), true);
            case CANCELLED -> finishLocked(contact, adapter, TerminalOutcome.ANNOUNCEMENT_CANCELLED,
                    ForegroundSessionRegistry.TerminalReason.CANCELLED, result.detail(), true);
        }
    }

    @Nullable
    private Contact contactByIdLocked(UUID contactId) {
        for (Contact contact : contactsByCitizen.values()) {
            if (contact.contactId.equals(contactId)) return contact;
        }
        return null;
    }

    private void finishLocked(
            Contact contact,
            Adapter adapter,
            TerminalOutcome outcome,
            @Nullable ForegroundSessionRegistry.TerminalReason reservationReason,
            String detail,
            boolean releaseReservation
    ) {
        if (!contactsByCitizen.remove(contact.citizenId, contact)) return;

        TerminalContact terminal = new TerminalContact(
                contact.contactId,
                contact.citizenId,
                contact.originPlayerId,
                outcome,
                detail == null || detail.isBlank() ? outcome.name().toLowerCase() : detail
        );
        // Remove ownership before side effects. stop/end may synchronously trigger provider callbacks.
        adapter.stopNavigation(contact.citizenId);
        contact.reservation.urgentWalking(false);
        if (releaseReservation && reservationReason != null) {
            contact.reservation.end(reservationReason, terminal.detail());
        }
        if (contact.completion != null) {
            contact.completion.accept(terminal);
        }
    }

    private Snapshot snapshotOf(Contact contact) {
        return new Snapshot(
                contact.contactId,
                contact.citizenId,
                contact.originPlayerId,
                contact.phase,
                contact.startedAtNanos,
                contact.lastRepathAtNanos
        );
    }

    private static final class Contact {
        final UUID contactId;
        final UUID citizenId;
        final UUID originPlayerId;
        Phase phase;
        final long startedAtNanos;
        long lastRepathAtNanos;
        final Reservation reservation;
        @Nullable final Consumer<TerminalContact> completion;

        Contact(
                UUID contactId,
                UUID citizenId,
                UUID originPlayerId,
                Phase phase,
                long startedAtNanos,
                long lastRepathAtNanos,
                Reservation reservation,
                @Nullable Consumer<TerminalContact> completion
        ) {
            this.contactId = contactId;
            this.citizenId = citizenId;
            this.originPlayerId = originPlayerId;
            this.phase = phase;
            this.startedAtNanos = startedAtNanos;
            this.lastRepathAtNanos = lastRepathAtNanos;
            this.reservation = reservation;
            this.completion = completion;
        }
    }
}
