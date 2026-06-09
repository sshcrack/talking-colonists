package me.sshcrack.mc_talking.util;

import com.minecolonies.api.colony.IColony;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.duck.ColonyEventDataProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ColonyEventBuffer {

    private ColonyEventBuffer() {}

    public enum EventType {
        RAID,
        CITIZEN_DEATH,
        CITIZEN_BORN,
        CITIZEN_HIRED,
        CITIZEN_RESURRECTED,
        CITIZEN_JOB_CHANGE,
        BUILDING_ADDED,
        BUILDING_REMOVED,
        BUILDING_UPGRADED,
        COLONY_FOUNDED
    }

    public record ColonyEvent(EventType type, String description, long timestampTicks) {
        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", type.name());
            tag.putString("description", description);
            tag.putLong("timestampTicks", timestampTicks);
            return tag;
        }

        public static ColonyEvent deserialize(CompoundTag tag) {
            try {
                return new ColonyEvent(
                        EventType.valueOf(tag.getString("type")),
                        tag.getString("description"),
                        tag.getLong("timestampTicks")
                );
            } catch (IllegalArgumentException e) {
                McTalking.LOGGER.warn("Skipping corrupt colony event: unknown type '{}'", tag.getString("type"));
                return null;
            }
        }
    }

    static final int MAX_EVENTS = 20;

    public static void trimEvents(ConcurrentLinkedDeque<ColonyEvent> buffer) {
        while (buffer.size() > MAX_EVENTS) {
            buffer.pollLast();
        }
    }

    private static ColonyEventDataProvider getProvider(IColony colony) {
        return (ColonyEventDataProvider) colony;
    }

    private static long currentTick(IColony colony) {
        Level world = colony.getWorld();
        if (world == null) return 0;
        return world.getGameTime();
    }

    public static void recordRaid(IColony colony, int lostCitizens) {
        long now = currentTick(colony);
        var provider = getProvider(colony);
        provider.mc_talking$setLastRaidEndTime(now);
        provider.mc_talking$setLastRaidLostCitizens(lostCitizens);
        recordEvent(colony, EventType.RAID, lostCitizens + " citizens lost in raid");
    }

    public static void recordEvent(IColony colony, EventType type, String description) {
        long now = currentTick(colony);
        var provider = getProvider(colony);
        var buffer = provider.mc_talking$getOrCreateEvents();
        buffer.addFirst(new ColonyEvent(type, description, now));
        while (buffer.size() > MAX_EVENTS) {
            buffer.pollLast();
        }
    }

    public static List<ColonyEvent> getRecentEvents(IColony colony, int maxAgeSeconds) {
        var provider = getProvider(colony);
        var buffer = provider.mc_talking$getOrCreateEvents();
        if (buffer.isEmpty()) return List.of();
        long gameTime = currentTick(colony);
        long cutoff = gameTime - (maxAgeSeconds * 20L);
        List<ColonyEvent> result = new ArrayList<>();
        for (ColonyEvent event : buffer) {
            if (event.timestampTicks() >= cutoff) {
                result.add(event);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean isInTrauma(IColony colony, int durationSeconds) {
        if (durationSeconds <= 0) return false;
        var provider = getProvider(colony);
        long lastEnd = provider.mc_talking$getLastRaidEndTime();
        if (lastEnd == Long.MAX_VALUE) return false;
        long gameTime = currentTick(colony);
        return (gameTime - lastEnd) < (durationSeconds * 20L);
    }

    public static long ticksSinceRaid(IColony colony) {
        var provider = getProvider(colony);
        long lastEnd = provider.mc_talking$getLastRaidEndTime();
        if (lastEnd == Long.MAX_VALUE) return Long.MAX_VALUE;
        long gameTime = currentTick(colony);
        return gameTime - lastEnd;
    }

    public static int getLostCitizens(IColony colony) {
        var provider = getProvider(colony);
        return provider.mc_talking$getLastRaidLostCitizens();
    }

    public static long getLastRaidEndTime(IColony colony) {
        var provider = getProvider(colony);
        return provider.mc_talking$getLastRaidEndTime();
    }
}
