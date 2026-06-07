package me.sshcrack.mc_talking.support;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import net.minecraft.server.level.ServerPlayer;

public class PlayerFulfillmentHandler {
    private static final ThreadLocal<ServerPlayer> CURRENT_FULFILLER = new ThreadLocal<>();

    private PlayerFulfillmentHandler() {
    }

    public static void setPendingFulfiller(ServerPlayer player) {
        CURRENT_FULFILLER.set(player);
    }

    public static ServerPlayer consumePendingFulfiller() {
        ServerPlayer p = CURRENT_FULFILLER.get();
        CURRENT_FULFILLER.remove();
        return p;
    }

    public static void tryClearPending() {
        if (CURRENT_FULFILLER.get() != null) {
            CURRENT_FULFILLER.remove();
        }
    }

    public static void onRequestOverruled(IRequestManager manager, IRequest<?> request) {
        IColony colony = manager.getColony();
        ServerPlayer player = consumePendingFulfiller();
        if (player == null) return;

        String itemName = request.getShortDisplayString().getString();
        String playerName = player.getName().getString();
        ICitizenData foundCitizen = null;

        var requesterPos = request.getRequester().getLocation().getInDimensionLocation();

        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            IBuilding workBuilding = citizen.getWorkBuilding();
            if (workBuilding != null && requesterPos.equals(workBuilding.getPosition())) {
                foundCitizen = citizen;
                break;
            }
        }

        if (foundCitizen == null) {
            McTalking.LOGGER.warn("[Fulfillment] No citizen found for requester at {}; request {} not attributed.",
                    requesterPos, request.getId());
            return;
        }

        var mem = ((CitizenDataMemoryExtended) foundCitizen).mc_talking$getOrInitializeMemory();
        mem.addEvent(String.format(
                CitizenMemories.SYSTEM_EVENT_PREFIX + " The player %s brought me the %s I needed. I'm grateful.",
                playerName, itemName));

        McTalking.LOGGER.info("[Fulfillment] Citizen {} remembers fulfillment by {} of {}",
                foundCitizen.getName(), playerName, itemName);
    }
}
