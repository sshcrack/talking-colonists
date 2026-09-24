package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Options for one addon-started player conversation, such as a quest giver or judge. They apply
 * only to the session they start: the agenda is not stored and never reaches later conversations.
 *
 * @param agenda        extra instructions added to this session's system prompt, or {@code null}
 * @param tools         which addon tools the citizen may use; core tools keep their normal policy
 * @param purpose       addon tag reported in this session's lifecycle events, or {@code null}
 * @param extractMemory whether the conversation is summarised into citizen memory as usual
 */
public record PlayerConversationOptions(
        @Nullable String agenda,
        @NotNull ControlledConversationOptions tools,
        @Nullable String purpose,
        boolean extractMemory
) {
    /** Longest accepted agenda. */
    public static final int MAX_AGENDA_LENGTH = 2000;
    private static final Pattern PURPOSE = Pattern.compile("[a-z0-9_.:-]{1,64}");
    private static final PlayerConversationOptions DEFAULTS =
            new PlayerConversationOptions(null, ControlledConversationOptions.allAddonTools(), null, true);

    public PlayerConversationOptions {
        Objects.requireNonNull(tools, "tools");
        if (agenda != null) {
            agenda = agenda.strip();
            if (agenda.isEmpty()) agenda = null;
            else if (agenda.length() > MAX_AGENDA_LENGTH) {
                throw new IllegalArgumentException("agenda must be at most " + MAX_AGENDA_LENGTH + " characters");
            }
        }
        if (purpose != null && !PURPOSE.matcher(purpose).matches()) {
            throw new IllegalArgumentException("purpose must match " + PURPOSE.pattern());
        }
    }

    /** Behaves exactly like {@code startPlayerConversation(player, citizen)}. */
    public static @NotNull PlayerConversationOptions defaults() {
        return DEFAULTS;
    }

    public @NotNull PlayerConversationOptions withAgenda(@Nullable String agenda) {
        return new PlayerConversationOptions(agenda, tools, purpose, extractMemory);
    }

    public @NotNull PlayerConversationOptions withTools(@NotNull ControlledConversationOptions tools) {
        return new PlayerConversationOptions(agenda, tools, purpose, extractMemory);
    }

    public @NotNull PlayerConversationOptions withPurpose(@Nullable String purpose) {
        return new PlayerConversationOptions(agenda, tools, purpose, extractMemory);
    }

    public @NotNull PlayerConversationOptions withMemoryExtraction(boolean extractMemory) {
        return new PlayerConversationOptions(agenda, tools, purpose, extractMemory);
    }

    /** Whether these options change nothing compared with a normal player conversation. */
    public boolean isDefault() {
        return equals(DEFAULTS);
    }
}
