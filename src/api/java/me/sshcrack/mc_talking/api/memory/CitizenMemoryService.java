package me.sshcrack.mc_talking.api.memory;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Supported addon access to citizen memory without casting MineColonies data to Talking Colonists
 * mixin interfaces or mutating internal collections directly. Synchronous operations are marshalled
 * to the owning Minecraft server thread before internal memory state is read or changed.
 *
 * <p>Low-level fact/event/relationship writes use {@link MemoryProvenance#ADDON_DIRECT_WRITE}; they
 * are not authenticated player statements. Use {@link #confirmOutcome(ICitizenData, AddonConfirmedOutcome)}
 * for authoritative gameplay outcomes and controlled-session transcripts for player speech.</p>
 */
public final class CitizenMemoryService {
    private CitizenMemoryService() {
    }

    public static boolean addEvent(@NotNull AbstractEntityCitizen citizen, @NotNull String event) {
        return citizen.getCitizenData() != null && addEvent(citizen.getCitizenData(), event);
    }

    public static boolean addEvent(@NotNull ICitizenData citizen, @NotNull String event) {
        return TalkingColonistsApi.services().memory().addEvent(citizen, event);
    }

    /** Removes one exact event string if present. */
    public static boolean removeEvent(@NotNull AbstractEntityCitizen citizen, @NotNull String event) {
        return citizen.getCitizenData() != null && removeEvent(citizen.getCitizenData(), event);
    }

    public static boolean removeEvent(@NotNull ICitizenData citizen, @NotNull String event) {
        return TalkingColonistsApi.services().memory().removeEvent(citizen, event);
    }

    public static boolean addFact(@NotNull AbstractEntityCitizen citizen, @NotNull String fact) {
        return citizen.getCitizenData() != null && addFact(citizen.getCitizenData(), fact);
    }

    public static boolean addFact(@NotNull ICitizenData citizen, @NotNull String fact) {
        return TalkingColonistsApi.services().memory().addFact(citizen, fact);
    }

    /** Removes one exact fact string if present. */
    public static boolean removeFact(@NotNull AbstractEntityCitizen citizen, @NotNull String fact) {
        return citizen.getCitizenData() != null && removeFact(citizen.getCitizenData(), fact);
    }

    public static boolean removeFact(@NotNull ICitizenData citizen, @NotNull String fact) {
        return TalkingColonistsApi.services().memory().removeFact(citizen, fact);
    }

    /**
     * Applies a bounded relationship/personality delta. {@code delta} must be finite and within
     * [-1, 1], matching core-generated relationship changes.
     */
    public static boolean addRelationshipChange(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull UUID targetId,
            @NotNull CitizenRelationshipDimension dimension,
            float delta
    ) {
        return citizen.getCitizenData() != null
                && addRelationshipChange(citizen.getCitizenData(), targetId, dimension, delta);
    }

    public static boolean addRelationshipChange(
            @NotNull ICitizenData citizen,
            @NotNull UUID targetId,
            @NotNull CitizenRelationshipDimension dimension,
            float delta
    ) {
        return TalkingColonistsApi.services().memory().addRelationshipChange(
                citizen, targetId, dimension, delta);
    }

    /**
     * Persists an authoritative addon outcome exactly once. Retries with the same source/idempotency
     * pair return {@link AddonMemoryWriteResult#DUPLICATE} and do not reapply relationship deltas.
     */
    public static @NotNull AddonMemoryWriteResult confirmOutcome(
            @NotNull ICitizenData citizen,
            @NotNull AddonConfirmedOutcome outcome
    ) {
        return TalkingColonistsApi.services().memory().confirmOutcome(citizen, outcome);
    }

    public static @NotNull AddonMemoryWriteResult confirmOutcome(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull AddonConfirmedOutcome outcome
    ) {
        return citizen.getCitizenData() == null
                ? AddonMemoryWriteResult.UNAVAILABLE
                : confirmOutcome(citizen.getCitizenData(), outcome);
    }

    public static @NotNull Optional<CitizenMemorySnapshot> snapshot(@NotNull AbstractEntityCitizen citizen) {
        return citizen.getCitizenData() == null
                ? Optional.empty()
                : snapshot(citizen.getCitizenData());
    }

    public static @NotNull Optional<CitizenMemorySnapshot> snapshot(@NotNull ICitizenData citizen) {
        return TalkingColonistsApi.services().memory().snapshot(citizen);
    }

    /**
     * Publishes a colony broadcast, the same kind the {@code initiate_broadcast} AI tool creates.
     * Citizens remember it with the request's source, include it in their prompts, spread it to each
     * other and, if allowed, announce it aloud near players. Publishing is rate limited per colony.
     *
     * <p>Requires {@link ApiFeature#BROADCAST_PUBLISHING}; check
     * {@link TalkingColonistsApi#supports(ApiFeature)} first when running on older 2.x runtimes.</p>
     */
    public static @NotNull BroadcastPublishResult publishBroadcast(@NotNull IColony colony,
                                                                   @NotNull BroadcastRequest request) {
        TalkingColonistsApi.requireSupported(ApiFeature.BROADCAST_PUBLISHING);
        return TalkingColonistsApi.services().memory().publishBroadcast(colony, request);
    }

    /**
     * Makes every citizen of {@code colony} forget a published broadcast so it stops spreading.
     * Returns whether any citizen still remembered it. Requires {@link ApiFeature#BROADCAST_PUBLISHING}.
     */
    public static boolean retractBroadcast(@NotNull IColony colony, @NotNull String broadcastId) {
        TalkingColonistsApi.requireSupported(ApiFeature.BROADCAST_PUBLISHING);
        return TalkingColonistsApi.services().memory().retractBroadcast(colony, broadcastId);
    }

    /**
     * How many citizens of {@code colony} currently remember a published broadcast, out of all its
     * citizens; empty when no citizen remembers it (unknown, retracted or expired). Use it to tell
     * the player how far their news has spread. Requires {@link ApiFeature#BROADCAST_REACH}.
     */
    public static @NotNull Optional<BroadcastReach> broadcastReach(@NotNull IColony colony, @NotNull String broadcastId) {
        TalkingColonistsApi.requireSupported(ApiFeature.BROADCAST_REACH);
        return TalkingColonistsApi.services().memory().broadcastReach(colony, broadcastId);
    }
}
