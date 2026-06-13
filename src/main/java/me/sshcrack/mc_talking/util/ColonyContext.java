package me.sshcrack.mc_talking.util;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.handler.ConstructionEventTracker;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ColonyContext(
    boolean hasResidenceUnderConstruction,
    boolean hasCookUnderConstruction,
    boolean hasTavernUnderConstruction,
    boolean hasWorkplaceUnderConstruction,
    boolean hasAvailableUnassignedResidence,
    boolean hasRestaurantWithoutCook,
    List<ConstructionEventTracker.CompletedEvent> recentCompletions,
    double residenceMitigationFactor,
    double foodMitigationFactor,
    double jobMitigationFactor,
    double unassignedResidenceFactor,
    double noCookFactor,
    long constructionEventRetentionTicks
) {

    public static ColonyContext compute(ICitizenData data, McTalkingConfig config) {
        IColony colony = data.getColony();
        var buildings = colony.getServerBuildingManager().getBuildings().values();

        boolean resUnderCon   = false;
        boolean cookUnderCon  = false;
        boolean tavUnderCon   = false;
        boolean jobUnderCon   = false;
        boolean availRes      = false;
        boolean noChef        = false;

        for (IBuilding b : buildings) {
            String path = b.getBuildingType().getRegistryName().getPath();
            boolean pending = b.isPendingConstruction();

            switch (path) {
                case "residence" -> {
                    if (pending) resUnderCon = true;
                    if (b.isBuilt() && b.getBuildingLevel() > 0
                            && b.getAllAssignedCitizen().isEmpty()) availRes = true;
                }
                case "cook", "restaurant" -> {
                    if (pending) cookUnderCon = true;
                    if (b.isBuilt() && b.getAllAssignedCitizen().isEmpty()) noChef = true;
                }
                case "tavern" -> { if (pending) tavUnderCon = true; }
            }
        }

        IBuilding work = data.getWorkBuilding();
        if (work != null && work.isPendingConstruction()) jobUnderCon = true;

        long gameTime = colony.getWorld() != null ? colony.getWorld().getGameTime() : 0;
        long retention = config.constructionEventRetentionTicks;
        List<ConstructionEventTracker.CompletedEvent> recent =
            ConstructionEventTracker.getRelevantEvents(data, gameTime, retention);

        return new ColonyContext(
            resUnderCon, cookUnderCon, tavUnderCon, jobUnderCon,
            availRes, noChef,
            recent,
            config.residenceMitigationFactor,
            config.foodMitigationFactor,
            config.jobMitigationFactor,
            config.unassignedResidenceFactor,
            config.noCookFactor,
            retention
        );
    }

    public long applyMitigation(HappinessModifierType type, long rawTicks) {
        return switch (type) {
            case HOMELESSNESS -> {
                double f = 1.0;
                if (hasResidenceUnderConstruction)   f *= residenceMitigationFactor;
                if (hasAvailableUnassignedResidence) f *= unassignedResidenceFactor;
                yield (long)(rawTicks * f);
            }
            case FOOD -> {
                double f = 1.0;
                if (hasCookUnderConstruction || hasTavernUnderConstruction) f *= foodMitigationFactor;
                if (hasRestaurantWithoutCook)                                f *= noCookFactor;
                yield (long)(rawTicks * f);
            }
            case IDLEATJOB -> hasWorkplaceUnderConstruction
                ? (long)(rawTicks * jobMitigationFactor) : rawTicks;
            default -> rawTicks;
        };
    }

    public @Nullable String getNotes(HappinessModifierType type) {
        for (var e : recentCompletions) {
            String path = e.buildingTypeId().getPath();
            String note = switch (type) {
                case HOMELESSNESS -> switch (path) {
                    case "residence" -> switch (e.type()) {
                        case BUILD    -> "A new home was just finished nearby!";
                        case UPGRADE  -> "More housing capacity was just completed nearby!";
                        case REPAIR   -> "Your home was just repaired!";
                        default       -> null;
                    };
                    default -> null;
                };
                case FOOD -> switch (path) {
                    case "cook", "restaurant" -> switch (e.type()) {
                        case BUILD   -> "A new kitchen just opened — food should be available soon!";
                        case UPGRADE -> "The kitchen was just upgraded!";
                        case REPAIR  -> "The kitchen was just repaired and is back in service!";
                        default      -> null;
                    };
                    case "tavern" -> "The tavern just reopened after renovations!";
                    default -> null;
                };
                case IDLEATJOB, UNEMPLOYMENT -> { yield null; }
                default -> null;
            };
            if (note != null) return note;
        }

        return switch (type) {
            case HOMELESSNESS -> {
                if (hasResidenceUnderConstruction)
                    yield "A new residence is being built — there's hope on the horizon.";
                if (hasAvailableUnassignedResidence)
                    yield "There are homes available, but you haven't been assigned one yet.";
                yield null;
            }
            case FOOD -> {
                if (hasCookUnderConstruction || hasTavernUnderConstruction)
                    yield "A kitchen is being set up — food should be coming soon.";
                if (hasRestaurantWithoutCook)
                    yield "There's a restaurant here, but no cook has been assigned to it yet.";
                yield null;
            }
            case IDLEATJOB -> {
                if (hasWorkplaceUnderConstruction)
                    yield "Your workplace is being upgraded — hang in there.";
                yield null;
            }
            default -> null;
        };
    }
}
