package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Colony/world context relevant to this citizen's prompt. */
public record ColonyPromptView(
        int id,
        @NotNull String name,
        boolean peaceful,
        @Nullable String foundingPlayer,
        int ageDays,
        @Nullable Long lastRaidEndTimeTicks,
        int lastRaidLostCitizens,
        long currentGameTimeTicks,
        @NotNull List<String> recentEvents,
        @NotNull List<String> connections,
        @Nullable String milestone,
        @Nullable String environment
) {
    public ColonyPromptView {
        recentEvents = List.copyOf(recentEvents);
        connections = List.copyOf(connections);
    }
}
