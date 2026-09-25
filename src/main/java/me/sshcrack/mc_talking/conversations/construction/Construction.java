package me.sshcrack.mc_talking.conversations.construction;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import me.sshcrack.mc_talking.api.prompt.view.CitizenRequestAvailabilityView;
import me.sshcrack.mc_talking.manager.prompt.CitizenWorkViews;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads the colony's building jobs from MineColonies: every work order, the builder who took it on, how
 * much of it is built (the same measure as the builder's window: materials still needed against all it
 * takes) and what the builder is still waiting for. Server thread only.
 */
public final class Construction {
    /** More than this and the prompt names only the first ones, claimed jobs first. */
    static final int MAX_SITES = 5;

    private Construction() {
    }

    public static @Nullable ConstructionContext context(IColony colony) {
        List<IServerWorkOrder> orders = new ArrayList<>(colony.getWorkManager().getWorkOrders().values());
        if (orders.isEmpty()) return null;
        orders.sort(Comparator.comparing((IServerWorkOrder order) -> !order.isClaimed())
                .thenComparingInt(order -> -order.getPriority()));
        List<ConstructionSite> sites = new ArrayList<>();
        for (IServerWorkOrder order : orders.subList(0, Math.min(MAX_SITES, orders.size()))) {
            sites.add(site(colony, order));
        }
        return new ConstructionContext(sites);
    }

    private static ConstructionSite site(IColony colony, IServerWorkOrder order) {
        IBuilding target = colony.getServerBuildingManager().getBuilding(order.getLocation());
        String name = target != null ? buildingName(target) : orderName(order);
        ConstructionSite.Kind kind = kind(order);
        AbstractBuildingStructureBuilder hut = order.isClaimed()
                && colony.getServerBuildingManager().getBuilding(order.getClaimedBy()) instanceof AbstractBuildingStructureBuilder builderHut
                ? builderHut : null;
        ICitizenData builder = hut == null ? null : hut.getAllAssignedCitizen().stream().findFirst().orElse(null);
        if (hut == null || builder == null) {
            return new ConstructionSite(name, kind, order.getTargetLevel(), null, null, -1, null, List.of(), List.of());
        }
        IBuilderWorkOrder current = hut.getWorkOrder();
        boolean working = current != null && current.getID() == order.getID();
        int percent = working ? percent(hut, current) : -1;
        String stage = working && current.getStage() != null ? current.getStage().name() : null;
        List<String> missing = List.of();
        List<String> onTheWay = List.of();
        if (working) {
            CitizenRequestAvailabilityView requests = CitizenWorkViews.extractRequestSnapshot(builder, hut, 0).observation().value();
            if (requests != null) {
                missing = requests.waitingForResolver();
                onTheWay = requests.assignedOrInProgress();
            }
        }
        return new ConstructionSite(name, kind, order.getTargetLevel(), builder.getName(), builder.getUUID(), percent, stage,
                missing, onTheWay);
    }

    /** Like MineColonies' builder window: 100% minus the share of materials still to be placed. */
    private static int percent(AbstractBuildingStructureBuilder hut, IBuilderWorkOrder order) {
        int total = order.getAmountOfResources();
        if (total <= 0) return -1;
        long needed = 0;
        for (ItemStorage resource : hut.getNeededResources().values()) needed += resource.getAmount();
        return (int) Math.max(0, Math.min(100, 100 - needed * 100 / total));
    }

    private static ConstructionSite.Kind kind(IServerWorkOrder order) {
        WorkOrderType type = order.getWorkOrderType();
        if (type == WorkOrderType.REMOVE) return ConstructionSite.Kind.REMOVE;
        if (type == WorkOrderType.REPAIR) return ConstructionSite.Kind.REPAIR;
        return order.getTargetLevel() <= 1 && order.getCurrentLevel() == 0 ? ConstructionSite.Kind.NEW : ConstructionSite.Kind.UPGRADE;
    }

    /** The hut's name as players see it; translation keys (dedicated servers) fall back to its type. */
    static String buildingName(IBuilding building) {
        String display = building.getBuildingDisplayName();
        if (readable(display)) return display;
        String translated = Component.translatable(building.getBuildingType().getTranslationKey()).getString();
        if (readable(translated)) return translated;
        return capitalize(building.getBuildingType().getRegistryName().getPath());
    }

    /** Decorations and other non-hut work orders: their display name, else the blueprint's file name. */
    private static String orderName(IServerWorkOrder order) {
        String display = order.getDisplayName().getString();
        if (readable(display)) return display;
        String path = order.getStructurePath();
        String file = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1).replace(".blueprint", "");
        return capitalize(file.replace('_', ' '));
    }

    private static boolean readable(@Nullable String name) {
        return name != null && !name.isBlank() && !name.contains(".") && !name.contains("/");
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
