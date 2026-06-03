package me.sshcrack.mc_talking.pregen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerHeatmapTracker {
    private PlayerHeatmapTracker() {}

    private static final Map<UUID, Map<UUID, Long>> scores = new ConcurrentHashMap<>();

    public static void recordProximity(UUID playerId, UUID citizenId) {
        scores.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .merge(citizenId, 1L, Long::sum);
    }

    public static boolean isPlayerNearby(ServerLevel level, double x, double y, double z, double range) {
        AABB box = new AABB(x - range, y - range, z - range, x + range, y + range, z + range);
        return !level.getEntitiesOfClass(ServerPlayer.class, box).isEmpty();
    }

    public static void onPlayerRemoved(UUID playerId) {
        scores.remove(playerId);
    }

    public static void cleanup() {
        scores.clear();
    }
}
