package me.sshcrack.mc_talking.util;

import org.jetbrains.annotations.Nullable;

/**
 * Decides whether a chat line from a player who is in a conversation goes to the citizen
 * instead of server chat (roadmap Q10). Pure so it is unit-tested.
 */
public final class ChatToCitizenRouter {
    private ChatToCitizenRouter() {
    }

    /**
     * @param message    the raw chat line
     * @param prefix     the configured prefix (e.g. {@code "@"}); blank disables prefix routing
     * @param allToggled whether the player sends every chat line to the citizen
     * @return the text for the citizen, or null when the line stays in server chat
     */
    public static @Nullable String citizenText(String message, String prefix, boolean allToggled) {
        String text = message.strip();
        String trimmedPrefix = prefix == null ? "" : prefix.strip();
        if (!trimmedPrefix.isEmpty() && text.startsWith(trimmedPrefix)) {
            String rest = text.substring(trimmedPrefix.length()).strip();
            return rest.isEmpty() ? null : rest;
        }
        if (allToggled && !text.isEmpty()) return text;
        return null;
    }
}
