package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/** Context specific to the current conversation/player and response language. */
public record ConversationPromptView(
        @NotNull String responseLanguageName,
        @Nullable PlayerRelationView playerRelation,
        @Nullable String playerState,
        @NotNull Map<UUID, String> interestedParties
) {
    public ConversationPromptView {
        interestedParties = Map.copyOf(interestedParties);
    }
}
