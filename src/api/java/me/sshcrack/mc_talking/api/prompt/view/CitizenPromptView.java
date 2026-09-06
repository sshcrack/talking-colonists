package me.sshcrack.mc_talking.api.prompt.view;

import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only snapshot of the core context Talking Colonists has assembled for a citizen prompt.
 *
 * <p>The concrete implementation is owned by Talking Colonists. Addons consume this interface from
 * prompt contributors/providers instead of constructing or mirroring core/MineColonies state.
 * The grouped views are intentionally coarse-grained so fields can evolve without a giant public
 * constructor becoming part of the binary compatibility contract.</p>
 */
public interface CitizenPromptView {
    @NotNull CitizenIdentityView identity();

    @NotNull CitizenFamilyView family();

    @NotNull CitizenWellbeingView wellbeing();

    @NotNull CitizenWorkView work();

    @NotNull ColonyPromptView colony();

    @NotNull ConversationPromptView conversation();

    @NotNull CitizenActivityView activity();

    @Nullable CitizenMemorySnapshot memories();
}
