package me.sshcrack.mc_talking.internal.session;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Owns a rolling-window ambient speech budget per player.
 *
 * <p>Every player accumulates a timestamp each time an ambient line (greeting, mumble, voiced
 * rumor/broadcast, random citizen-to-citizen conversation, addon ambient line, ...) is spoken
 * within earshot of them. {@link #tryConsume} checks every listener at once and only records the
 * attempt if <em>all</em> of them still have room in the window — a line that would be heard by
 * one player who is already over budget is skipped entirely rather than partially "spent" against
 * the players who still have room. This keeps the guarantee simple: a player never hears more than
 * {@code maxLines} ambient lines per rolling window, regardless of how many other players are also
 * in earshot.</p>
 */
public final class AmbientSpeechBudgetRegistry {
    private final LongSupplier millisClock;
    private final Map<UUID, Deque<Long>> timestampsByPlayer = new LinkedHashMap<>();

    public AmbientSpeechBudgetRegistry(LongSupplier millisClock) {
        this.millisClock = Objects.requireNonNull(millisClock, "millisClock");
    }

    /**
     * Read-only check: would every player in {@code hearingPlayerIds} currently have room for one
     * more ambient line within {@code windowMillis}? Does not record anything, so repeated calls
     * (for example while a handler scans many candidate citizens before picking one to actually
     * speak) never spend budget on their own.
     */
    public synchronized boolean hasCapacity(Collection<UUID> hearingPlayerIds, int maxLines, long windowMillis) {
        Objects.requireNonNull(hearingPlayerIds, "hearingPlayerIds");
        if (maxLines <= 0) throw new IllegalArgumentException("maxLines must be positive");
        if (windowMillis <= 0) throw new IllegalArgumentException("windowMillis must be positive");
        if (hearingPlayerIds.isEmpty()) return true;

        long now = millisClock.getAsLong();
        for (UUID playerId : hearingPlayerIds) {
            Deque<Long> timestamps = timestampsByPlayer.get(playerId);
            if (timestamps == null) continue;
            purgeExpired(timestamps, now, windowMillis);
            if (timestamps.size() >= maxLines) return false;
        }
        return true;
    }

    /**
     * Attempts to spend one unit of ambient speech budget against every player in
     * {@code hearingPlayerIds}. Returns {@code true} (and records the attempt for each listener)
     * only if every listener currently has room within {@code windowMillis}; otherwise no state is
     * mutated and {@code false} is returned.
     *
     * @param hearingPlayerIds players who would hear the ambient line
     * @param maxLines         maximum lines a player may hear per rolling window (must be positive)
     * @param windowMillis     rolling window length in milliseconds (must be positive)
     */
    public synchronized boolean tryConsume(Collection<UUID> hearingPlayerIds, int maxLines, long windowMillis) {
        Objects.requireNonNull(hearingPlayerIds, "hearingPlayerIds");
        if (maxLines <= 0) throw new IllegalArgumentException("maxLines must be positive");
        if (windowMillis <= 0) throw new IllegalArgumentException("windowMillis must be positive");
        if (hearingPlayerIds.isEmpty()) return true;

        long now = millisClock.getAsLong();
        for (UUID playerId : hearingPlayerIds) {
            Deque<Long> timestamps = timestampsByPlayer.get(playerId);
            if (timestamps == null) continue;
            purgeExpired(timestamps, now, windowMillis);
            if (timestamps.size() >= maxLines) return false;
        }

        for (UUID playerId : hearingPlayerIds) {
            timestampsByPlayer.computeIfAbsent(playerId, ignored -> new ArrayDeque<>()).addLast(now);
        }
        return true;
    }

    private static void purgeExpired(Deque<Long> timestamps, long now, long windowMillis) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= windowMillis) {
            timestamps.pollFirst();
        }
    }

    /** Removes all recorded budget state, e.g. on server shutdown. */
    public synchronized void clear() {
        timestampsByPlayer.clear();
    }
}
