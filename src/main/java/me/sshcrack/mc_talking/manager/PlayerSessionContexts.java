package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.conversation.PlayerConversationOptions;
import me.sshcrack.mc_talking.api.prompt.PromptSessionContext;
import org.jetbrains.annotations.NotNull;

/** Turns addon {@link PlayerConversationOptions} into the context of one player conversation. */
public final class PlayerSessionContexts {
    private PlayerSessionContexts() {
    }

    public static @NotNull PromptSessionContext of(@NotNull PlayerConversationOptions options) {
        return new PromptSessionContext(null, null, options.agenda(),
                options.tools().allowAllAddonTools(), options.tools().allowedAddonTools());
    }

    /** Adds the addon agenda of a player conversation to that session's system prompt only. */
    public static @NotNull String withAgenda(@NotNull String prompt, @NotNull PromptSessionContext session) {
        if (session.agenda() == null || session.isControlledTurn()) return prompt;
        return prompt + "\n\n## This conversation\n"
                + "Keep your personality, but follow this for the current conversation only:\n"
                + session.agenda();
    }
}
