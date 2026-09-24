package me.sshcrack.mc_talking.broadcast;

import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes news visible: a small arc of particles travels from the citizen who tells something to
 * the one who hears it (white for rumors, gold for colony broadcasts), and a ring spreads from
 * where a broadcast was announced. Vanilla particles sent by the server, so clients need nothing.
 * Server thread only.
 */
public final class NewsParticles {
    public enum Kind {
        RUMOR(ParticleTypes.END_ROD),
        BROADCAST(ParticleTypes.WAX_ON);

        final ParticleOptions particle;

        Kind(ParticleOptions particle) {
            this.particle = particle;
        }
    }

    static final int TRAIL_TICKS = 16;
    static final int MAX_ACTIVE = 64;
    private static final double ARC_HEIGHT = 0.8;

    private record Trail(ServerLevel level, Entity from, Entity to, Kind kind, int[] age) {
    }

    private static final List<Trail> TRAILS = new ArrayList<>();

    private NewsParticles() {
    }

    private static boolean enabled() {
        return McTalkingConfig.INSTANCE.instance().showNewsParticles;
    }

    /** Sends a particle arc from {@code from}'s head to {@code to}'s head over a little under a second. */
    public static void trail(Entity from, Entity to, Kind kind) {
        if (!enabled() || from == to || !(from.level() instanceof ServerLevel level) || to.level() != level) return;
        if (TRAILS.size() >= MAX_ACTIVE) return;
        TRAILS.add(new Trail(level, from, to, kind, new int[]{0}));
    }

    /** A ring of particles spreading around {@code origin}, e.g. a rung bell. */
    public static void ring(ServerLevel level, BlockPos origin, Kind kind) {
        if (!enabled()) return;
        Vec3 center = Vec3.atCenterOf(origin);
        for (int radius = 1; radius <= 3; radius++) {
            int points = radius * 10;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                level.sendParticles(kind.particle, center.x + Math.cos(angle) * radius, center.y + 0.3,
                        center.z + Math.sin(angle) * radius, 1, 0, 0.05, 0, 0.01);
            }
        }
    }

    public static void tick() {
        if (TRAILS.isEmpty()) return;
        TRAILS.removeIf(trail -> {
            int age = ++trail.age()[0];
            if (age > TRAIL_TICKS || trail.from().isRemoved() || trail.to().isRemoved()) return true;
            Vec3 start = trail.from().getEyePosition();
            Vec3 end = trail.to().getEyePosition();
            Vec3 point = pointOnArc(start, end, age / (double) TRAIL_TICKS);
            trail.level().sendParticles(trail.kind().particle, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0);
            return false;
        });
    }

    public static void clear() {
        TRAILS.clear();
    }

    /** Straight line from {@code start} to {@code end}, lifted by a half sine so it arcs over heads. */
    static Vec3 pointOnArc(Vec3 start, Vec3 end, double t) {
        return start.lerp(end, t).add(0, Math.sin(Math.PI * t) * ARC_HEIGHT, 0);
    }
}
