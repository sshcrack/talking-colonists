package me.sshcrack.mc_talking.internal.session;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Owns automatic-conversation cooldown timestamps and the need signature captured at termination. */
public final class ConversationCooldownRegistry {
    private final LongSupplier millisClock;
    private final Map<UUID, Cooldown> cooldowns = new LinkedHashMap<>();

    public ConversationCooldownRegistry(LongSupplier millisClock) {
        this.millisClock = Objects.requireNonNull(millisClock, "millisClock");
    }

    public synchronized void record(UUID citizenId, String needSignature) {
        cooldowns.put(
                Objects.requireNonNull(citizenId, "citizenId"),
                new Cooldown(millisClock.getAsLong(), Objects.requireNonNull(needSignature, "needSignature"))
        );
    }

    public synchronized boolean isActive(UUID citizenId, String currentNeedSignature, long durationMillis) {
        Cooldown cooldown = cooldowns.get(citizenId);
        if (cooldown == null) return false;
        if (!cooldown.needSignature.equals(currentNeedSignature)) {
            cooldowns.remove(citizenId);
            return false;
        }
        if (durationMillis <= 0 || millisClock.getAsLong() - cooldown.endedAtMillis >= durationMillis) {
            cooldowns.remove(citizenId);
            return false;
        }
        return true;
    }

    public synchronized void reset(UUID citizenId) {
        cooldowns.remove(citizenId);
    }

    public synchronized Map<UUID, Long> endTimesSnapshot() {
        Map<UUID, Long> result = new LinkedHashMap<>();
        cooldowns.forEach((id, value) -> result.put(id, value.endedAtMillis));
        return Collections.unmodifiableMap(result);
    }

    public synchronized void clear() {
        cooldowns.clear();
    }

    private record Cooldown(long endedAtMillis, String needSignature) {
    }
}
