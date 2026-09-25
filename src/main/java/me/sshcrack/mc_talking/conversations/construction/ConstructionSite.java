package me.sshcrack.mc_talking.conversations.construction;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * One building job in the colony, as citizens see it: what is being built, by whom, how far along it
 * is, and what the builder is still waiting for.
 *
 * @param building  readable building name, e.g. "Residence"
 * @param kind      new, upgrade, repair or removal
 * @param level     the level it is being built or upgraded to
 * @param builder   the builder's name, or null while no builder has taken it on
 * @param builderId the builder's citizen UUID, or null
 * @param percent   how much of it is built (0–100, from the materials already used), or -1 while unknown
 * @param stage     MineColonies' building stage name (e.g. "BUILD_SOLID"), or null
 * @param missing   materials the builder waits for that nobody in the colony can provide
 * @param onTheWay  materials being brought to the builder
 */
public record ConstructionSite(String building, Kind kind, int level, @Nullable String builder, @Nullable UUID builderId,
                               int percent, @Nullable String stage, List<String> missing, List<String> onTheWay) {
    public enum Kind {
        NEW, UPGRADE, REPAIR, REMOVE
    }

    public ConstructionSite {
        missing = List.copyOf(missing);
        onTheWay = List.copyOf(onTheWay);
    }
}
