package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenFamilyView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenIdentityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWellbeingView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenVerifiedFactsView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWorkView;
import me.sshcrack.mc_talking.api.prompt.view.ColonyPromptView;
import me.sshcrack.mc_talking.api.prompt.view.ConversationPromptView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Internal immutable implementation of the addon-facing prompt snapshot interface. */
record CitizenPromptSnapshot(
        @NotNull CitizenIdentityView identity,
        @NotNull CitizenFamilyView family,
        @NotNull CitizenWellbeingView wellbeing,
        @NotNull CitizenWorkView work,
        @NotNull ColonyPromptView colony,
        @NotNull ConversationPromptView conversation,
        @NotNull CitizenActivityView activity,
        @NotNull CitizenVerifiedFactsView verifiedFacts,
        @Nullable CitizenMemorySnapshot memories
) implements CitizenPromptView {
}
