package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.workorders.WorkOrderBuilding;
import me.sshcrack.mc_talking.McTalking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ConstructionEventTracker {

    private ConstructionEventTracker() {}

    private static final Map<Integer, LinkedList<CompletedEvent>> RECENT = new HashMap<>();
    static final int MAX_EVENTS = 20;

    public record CompletedEvent(
        ResourceLocation buildingTypeId,
        String displayName,
        int level,
        WorkOrderType type,
        long completedGameTick
    ) {
        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putString("buildingType", buildingTypeId.toString());
            tag.putString("displayName", displayName);
            tag.putInt("level", level);
            tag.putString("woType", type.name());
            tag.putLong("tick", completedGameTick);
            return tag;
        }

        public static @Nullable CompletedEvent deserialize(CompoundTag tag) {
            try {
                return new CompletedEvent(
                    /*? if neoforge {*/
                    ResourceLocation.parse(tag.getString("buildingType")),
                    /*?}*/
                    /*? if forge {*/
                    /*new ResourceLocation(tag.getString("buildingType")),*//*?}*/
                    tag.getString("displayName"),
                    tag.getInt("level"),
                    WorkOrderType.valueOf(tag.getString("woType")),
                    tag.getLong("tick")
                );
            } catch (Exception e) {
                McTalking.LOGGER.warn("Skipping corrupt ConstructionEvent: {}", e.getMessage());
                return null;
            }
        }
    }

    public static void onConstructionComplete(IBuilding building, WorkOrderBuilding workOrder) {
        WorkOrderType type = workOrder.getWorkOrderType();
        if (type == WorkOrderType.REMOVE) return;

        int colonyId = building.getColony().getID();
        var events = RECENT.computeIfAbsent(colonyId, k -> new LinkedList<>());
        events.addFirst(new CompletedEvent(
            building.getBuildingType().getRegistryName(),
            building.getBuildingDisplayName(),
            building.getBuildingLevel(),
            type,
            building.getColony().getWorld().getGameTime()
        ));
        while (events.size() > MAX_EVENTS) events.removeLast();
    }

    public static List<CompletedEvent> getRelevantEvents(
            ICitizenData data, long currentGameTime, long retentionTicks) {
        var events = RECENT.get(data.getColony().getID());
        if (events == null) return List.of();
        return events.stream()
            .filter(e -> currentGameTime - e.completedGameTick() < retentionTicks)
            .filter(e -> isRelevant(e, data))
            .toList();
    }

    private static boolean isRelevant(CompletedEvent e, ICitizenData data) {
        String path = e.buildingTypeId().getPath();
        return switch (path) {
            case "residence", "cook", "restaurant", "tavern" -> true;
            default -> {
                IBuilding work = data.getWorkBuilding();
                yield work != null &&
                    work.getBuildingType().getRegistryName().equals(e.buildingTypeId());
            }
        };
    }

    public static ListTag serializeColony(int colonyId) {
        ListTag list = new ListTag();
        var events = RECENT.get(colonyId);
        if (events != null) {
            for (var e : events) list.add(e.serialize());
        }
        return list;
    }

    public static void deserializeColony(int colonyId, ListTag list) {
        LinkedList<CompletedEvent> events = new LinkedList<>();
        for (int i = 0; i < list.size(); i++) {
            CompletedEvent e = CompletedEvent.deserialize(list.getCompound(i));
            if (e != null) events.add(e);
        }
        if (!events.isEmpty()) RECENT.put(colonyId, events);
    }

    public static void clear() { RECENT.clear(); }
}
