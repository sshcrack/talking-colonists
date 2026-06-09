package me.sshcrack.mc_talking.listener;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenDiedModEvent;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenJobChangedModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingAddedModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingConstructionModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingRemovedModEvent;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import net.minecraft.network.chat.Component;

public final class ColonyEventSubscriber {

    private ColonyEventSubscriber() {}

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        var bus = IMinecoloniesAPI.getInstance().getEventBus();

        bus.subscribe(ColonyCreatedModEvent.class, event -> {
            IColony colony = event.getColony();
            String ownerName = colony.getPermissions().getOwnerName();
            int day = colony.getDay();
            ColonyEventBuffer.recordEvent(colony, ColonyEventBuffer.EventType.COLONY_FOUNDED,
                    "Colony was founded by " + ownerName + " on day " + day);
        });

        bus.subscribe(CitizenDiedModEvent.class, event -> {
            ICitizenData citizen = (ICitizenData) event.getCitizen();
            IColony colony = citizen.getColony();
            String name = citizen.getName();
            String source = event.getDamageSource() != null
                    ? event.getDamageSource().getMsgId()
                    : "unknown cause";
            ColonyEventBuffer.recordEvent(colony, ColonyEventBuffer.EventType.CITIZEN_DEATH,
                    name + " died (" + source + ")");
        });

        bus.subscribe(CitizenAddedModEvent.class, event -> {
            ICitizenData citizen = (ICitizenData) event.getCitizen();
            IColony colony = citizen.getColony();
            String name = citizen.getName();
            switch (event.getSource()) {
                case BORN:
                    ColonyEventBuffer.recordEvent(colony, ColonyEventBuffer.EventType.CITIZEN_BORN,
                            name + " was born");
                    break;
                case HIRED:
                    ColonyEventBuffer.recordEvent(colony, ColonyEventBuffer.EventType.CITIZEN_HIRED,
                            name + " was hired from the tavern");
                    break;
                case RESURRECTED:
                    ColonyEventBuffer.recordEvent(colony, ColonyEventBuffer.EventType.CITIZEN_RESURRECTED,
                            name + " was resurrected");
                    break;
                default:
                    break;
            }
        });

        bus.subscribe(CitizenJobChangedModEvent.class, event -> {
            ICitizenData citizen = (ICitizenData) event.getCitizen();
            IColony colony = citizen.getColony();
            String name = citizen.getName();
            JobEntry prevJob = event.getPreviousJob();
            String prevJobName = prevJob != null
                    ? Component.translatable(prevJob.getTranslationKey()).getString()
                    : "unemployed";
            String newJobName = citizen.getJob() != null
                    ? Component.translatable(citizen.getJob().getJobRegistryEntry().getTranslationKey()).getString()
                    : "unemployed";
            if (!prevJobName.equals(newJobName)) {
                ColonyEventBuffer.recordEvent(colony, ColonyEventBuffer.EventType.CITIZEN_JOB_CHANGE,
                        name + " changed job from " + prevJobName + " to " + newJobName);
            }
        });

        bus.subscribe(BuildingAddedModEvent.class, event -> {
            IBuilding building = event.getBuilding();
            IColony colony = building.getColony();
            String buildingName = building.getBuildingDisplayName();
            ColonyEventBuffer.recordEvent(colony, ColonyEventBuffer.EventType.BUILDING_ADDED,
                    buildingName + " was placed");
        });

        bus.subscribe(BuildingConstructionModEvent.class, event -> {
            IBuilding building = event.getBuilding();
            IColony colony = building.getColony();
            String buildingName = building.getBuildingDisplayName();
            int level = building.getBuildingLevel();
            if (level > 1) {
                ColonyEventBuffer.recordEvent(colony, ColonyEventBuffer.EventType.BUILDING_UPGRADED,
                        buildingName + " was upgraded to level " + level);
            }
        });

        bus.subscribe(BuildingRemovedModEvent.class, event -> {
            IBuilding building = event.getBuilding();
            IColony colony = building.getColony();
            String buildingName = building.getBuildingDisplayName();
            ColonyEventBuffer.recordEvent(colony, ColonyEventBuffer.EventType.BUILDING_REMOVED,
                    buildingName + " was destroyed");
        });
    }
}
