package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.Nullable;

/**
 * Visitor-only facts for a MineColonies tavern guest. A visitor has no job, home or family in the
 * colony, so those parts of the prompt view are empty for visitors.
 *
 * @param recruitCost      what recruiting this visitor costs, for example {@code "3 x Diamond"}, or null
 * @param daysInColony     whole colony days since Talking Colonists first saw this visitor
 */
public record VisitorPromptView(@Nullable String recruitCost, int daysInColony) {
    public VisitorPromptView {
        daysInColony = Math.max(0, daysInColony);
    }
}
