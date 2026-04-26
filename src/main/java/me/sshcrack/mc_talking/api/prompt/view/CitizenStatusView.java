package me.sshcrack.mc_talking.api.prompt.view;

import java.util.List;

/**
 * Stable status view used by prompt providers to render current activity.
 */
public record CitizenStatusView(
        CitizenStatusType type,
        String translationKey,
        List<String> contextValues
) {
}
