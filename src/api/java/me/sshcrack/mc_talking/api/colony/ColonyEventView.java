package me.sshcrack.mc_talking.api.colony;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable view of one recorded colony event.
 *
 * @param type           event kind; {@link ColonyEventType#ADDON} for addon events
 * @param addonNamespace recording addon's namespace for addon events, otherwise null
 * @param addonKey       addon-defined event key such as {@code "election_won"}, otherwise null
 * @param description    human-readable text, as citizens see it in their prompts
 * @param gameTime       level game time (ticks) when the event was recorded
 */
public record ColonyEventView(
        @NotNull ColonyEventType type,
        @Nullable String addonNamespace,
        @Nullable String addonKey,
        @NotNull String description,
        long gameTime
) {
    public ColonyEventView {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
    }

    public boolean isAddonEvent() {
        return type == ColonyEventType.ADDON;
    }
}
