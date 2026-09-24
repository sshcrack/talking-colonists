package me.sshcrack.mc_talking.internal.session;

import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who currently holds the audible floor, so unprompted speech does not talk over someone else.
 *
 * <p>Foreground provider sessions are read from their registry by the caller. Everything else that
 * speaks, or keeps a group together between lines (pregenerated clips, citizen pair conversations,
 * controlled sessions such as a campfire), {@linkplain #hold holds} the floor for its lifetime.
 * Members of one group never block each other's speech of the group's kind, but are engaged for
 * anything else: a campfire teller does not greet passers-by between stories.</p>
 */
public final class SpeechFloor {
    /** A position in one level; {@code level} only needs identity equality. */
    public record Voice(UUID id, Object level, double x, double y, double z) {
        public static Voice of(Entity entity) {
            return new Voice(entity.getUUID(), entity.level(), entity.getX(), entity.getY(), entity.getZ());
        }

        double distanceSq(Voice other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /** Citizens holding the floor together for speech of {@code kind}. */
    public record Group(ConversationKind kind, List<Entity> members) {
    }

    private static final Map<Object, Group> HOLDERS = new ConcurrentHashMap<>();

    private SpeechFloor() {
    }

    /** Keeps the floor for {@code speakers} until {@link #release} with the same owner. */
    public static void hold(Object owner, ConversationKind kind, Collection<? extends Entity> speakers) {
        HOLDERS.put(owner, new Group(kind, List.copyOf(speakers)));
    }

    public static void release(Object owner) {
        HOLDERS.remove(owner);
    }

    public static void clear() {
        HOLDERS.clear();
    }

    /** Snapshot of every holding group. */
    public static List<Group> groups() {
        return new ArrayList<>(HOLDERS.values());
    }

    /**
     * True when a listener close enough to hear {@code candidate} would also hear one of
     * {@code speakers}: starting now would talk over them. Speakers in {@code exempt} (the
     * candidate and its own group) do not count.
     */
    public static boolean wouldOverlap(Voice candidate, Collection<Voice> speakers, Collection<Voice> listeners,
                                       Set<UUID> exempt, double radius) {
        if (radius <= 0) return false;
        double radiusSq = radius * radius;
        for (Voice listener : listeners) {
            if (listener.level() != candidate.level() || listener.distanceSq(candidate) > radiusSq) continue;
            for (Voice speaker : speakers) {
                if (exempt.contains(speaker.id()) || speaker.level() != listener.level()) continue;
                if (speaker.distanceSq(listener) <= radiusSq) return true;
            }
        }
        return false;
    }
}
