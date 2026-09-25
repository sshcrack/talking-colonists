package me.sshcrack.mc_talking.conversations.construction;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** How citizens know about the colony's building jobs: one line per site. Pure text. */
public final class ConstructionPrompts {
    static final int MAX_MATERIALS = 3;

    private ConstructionPrompts() {
    }

    /** The section, or an empty string when nothing is being built. {@code self} is the citizen's UUID. */
    public static String section(List<ConstructionSite> sites, @Nullable UUID self) {
        if (sites.isEmpty()) return "";
        StringBuilder text = new StringBuilder("\n## CONSTRUCTION IN THE COLONY\n");
        for (ConstructionSite site : sites) text.append("- ").append(line(site, self)).append('\n');
        return text.toString();
    }

    /** "The Residence (upgrade to level 2), built by Anna: about half done, the walls are going up. ..." */
    public static String line(ConstructionSite site, @Nullable UUID self) {
        boolean mine = self != null && self.equals(site.builderId());
        StringBuilder text = new StringBuilder("The ").append(site.building()).append(" (").append(what(site)).append(")");
        if (site.builder() == null) {
            return text.append(": no builder has taken it on yet.").toString();
        }
        text.append(mine ? ", your job" : ", built by " + site.builder()).append(": ").append(progress(site.percent()));
        String stage = stage(site.stage(), site.kind());
        if (stage != null) text.append(", ").append(stage);
        text.append('.');
        String who = mine ? "You are" : site.builder() + " is";
        if (!site.missing().isEmpty()) {
            text.append(' ').append(who).append(" waiting for materials nobody in the colony has: ")
                    .append(materials(site.missing())).append('.');
        }
        if (!site.onTheWay().isEmpty()) {
            text.append(site.missing().isEmpty() ? " " + (mine ? "Your" : site.builder() + "'s") + " materials are on their way: "
                    : " On their way: ").append(materials(site.onTheWay())).append('.');
        }
        return text.toString();
    }

    static String what(ConstructionSite site) {
        return switch (site.kind()) {
            case NEW -> "new";
            case UPGRADE -> "upgrade to level " + site.level();
            case REPAIR -> "repair";
            case REMOVE -> "being taken down";
        };
    }

    /** From the materials used, like the builder's window: "about half done". */
    static String progress(int percent) {
        if (percent < 0) return "just getting started";
        if (percent < 10) return "only just started";
        if (percent < 35) return "about a quarter done";
        if (percent < 65) return "about half done";
        if (percent < 90) return "well along";
        return "nearly finished";
    }

    static @Nullable String stage(@Nullable String stage, ConstructionSite.Kind kind) {
        if (stage == null || kind == ConstructionSite.Kind.REMOVE) return null;
        return switch (stage) {
            case "CLEAR", "CLEAR_WATER", "CLEAR_NON_SOLIDS" -> "the site is being cleared";
            case "BUILD_SOLID", "WEAK_SOLID" -> "the walls are going up";
            case "DECORATE" -> "the finishing touches are going on";
            case "SPAWN" -> "it is almost ready to use";
            default -> null;
        };
    }

    private static String materials(List<String> items) {
        List<String> shown = items.subList(0, Math.min(MAX_MATERIALS, items.size()));
        String text = String.join(", ", shown);
        return items.size() > shown.size() ? text + " and more" : text;
    }
}
