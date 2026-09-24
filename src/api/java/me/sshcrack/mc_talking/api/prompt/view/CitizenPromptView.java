package me.sshcrack.mc_talking.api.prompt.view;

import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Read-only snapshot of the core context Talking Colonists has assembled for a citizen prompt.
 *
 * <p>The concrete implementation is owned by Talking Colonists. Addons consume this interface from
 * prompt contributors/providers instead of constructing or mirroring core/MineColonies state.
 * The grouped views are intentionally coarse-grained so fields can evolve without a giant public
 * constructor becoming part of the binary compatibility contract.</p>
 */
public interface CitizenPromptView {
    /** Stable MineColonies citizen UUID represented by this snapshot. */
    @NotNull UUID citizenId();

    /** Authoritative contextual player UUID, or null when this prompt has no player authority/context. */
    @Nullable UUID playerId();

    @NotNull CitizenIdentityView identity();

    @NotNull CitizenFamilyView family();

    @NotNull CitizenWellbeingView wellbeing();

    @NotNull CitizenWorkView work();

    @NotNull ColonyPromptView colony();

    @NotNull ConversationPromptView conversation();

    @NotNull CitizenActivityView activity();

    /** Current authoritative observations. Prefer these over contradictory recollections. */
    @NotNull CitizenVerifiedFactsView verifiedFacts();

    @Nullable CitizenMemorySnapshot memories();

    /**
     * Visitor facts when this snapshot describes a MineColonies visitor rather than a colonist
     * (API 2.1, {@link me.sshcrack.mc_talking.api.ApiFeature#VISITOR_SPEAKERS}); otherwise null.
     */
    default @Nullable VisitorPromptView visitor() {
        return null;
    }
}
