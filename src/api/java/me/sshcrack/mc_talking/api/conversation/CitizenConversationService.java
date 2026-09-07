package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Supported addon-facing conversation entry points and lifecycle observation. */
public final class CitizenConversationService {
    private CitizenConversationService() {
    }

    public static boolean isBusy(@NotNull AbstractEntityCitizen citizen) {
        return TalkingColonistsApi.services().conversations().isBusy(citizen);
    }

    /** Detailed speech eligibility without starting a provider session. */
    public static @NotNull ConversationEligibility eligibility(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull ConversationKind kind
    ) {
        return TalkingColonistsApi.services().conversations().eligibility(citizen, kind);
    }

    /** Convenience equivalent to {@code eligibility(citizen, kind).eligible()}. */
    public static boolean canSpeak(@NotNull AbstractEntityCitizen citizen, @NotNull ConversationKind kind) {
        return eligibility(citizen, kind).eligible();
    }

    /** Starts or switches to a player-owned conversation and explains any rejection. */
    public static @NotNull ConversationStartResult startPlayerConversation(
            @NotNull ServerPlayer player,
            @NotNull AbstractEntityCitizen citizen
    ) {
        return TalkingColonistsApi.services().conversations().startPlayerConversation(player, citizen);
    }

    /**
     * Starts one addon-directed ambient line and completes after audible playback reaches a
     * terminal state. Rejected starts are represented as {@link AmbientLineResult.Status#REJECTED}
     * with a typed rejection reason instead of an ambiguous {@code false}.
     */
    public static @NotNull CompletableFuture<AmbientLineResult> requestAmbientLine(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull String promptDirective
    ) {
        return TalkingColonistsApi.services().conversations().requestAmbientLine(citizen, promptDirective);
    }

    /** Observes core-managed audible conversation starts/ends without mixins or manager access. */
    public static @NotNull AddonRegistration registerLifecycleListener(
            @NotNull String id,
            int order,
            @NotNull ConversationLifecycleListener listener
    ) {
        return TalkingColonistsApi.services().conversations().registerLifecycleListener(id, order, listener);
    }

    /** Returns the active Talking Colonists conversation kind for this citizen, if any. */
    public static @NotNull Optional<ConversationKind> activeKind(@NotNull AbstractEntityCitizen citizen) {
        return TalkingColonistsApi.services().conversations().activeKind(citizen);
    }

    /**
     * Snapshot of the foreground Live provider, including startup and recovery. Empty when no
     * foreground Live client exists (including Flash-only work). Call on the server thread.
     * Gameplay ownership and provider readiness are deliberately separate concepts.
     */
    public static @NotNull Optional<ProviderSessionStatus> providerStatus(@NotNull AbstractEntityCitizen citizen) {
        return TalkingColonistsApi.services().conversations().providerStatus(citizen);
    }

    /** Returns the player currently speaking directly to this citizen, if any. */
    public static @NotNull Optional<UUID> activePlayerId(@NotNull AbstractEntityCitizen citizen) {
        return TalkingColonistsApi.services().conversations().activePlayerId(citizen);
    }

    /** Returns whether the player currently owns a direct Talking Colonists conversation. */
    public static boolean isPlayerInConversation(@NotNull ServerPlayer player) {
        return TalkingColonistsApi.services().conversations().isPlayerInConversation(player);
    }

    /** Returns true only for genuinely free low-priority provider capacity. */
    public static boolean hasAmbientCapacity(int slotsNeeded) {
        return TalkingColonistsApi.services().conversations().hasAmbientCapacity(slotsNeeded);
    }

    /** Returns whether any online player in the same dimension is within {@code range} blocks. */
    public static boolean hasPlayerNearby(@NotNull AbstractEntityCitizen citizen, double range) {
        return TalkingColonistsApi.services().conversations().hasPlayerNearby(citizen, range);
    }

    /** Requests the active citizen session to finish audible playback and close. */
    public static boolean requestGracefulEnd(@NotNull AbstractEntityCitizen citizen) {
        return TalkingColonistsApi.services().conversations().requestGracefulEnd(citizen);
    }

    /** Reserves a citizen for addon gameplay with a ten-minute renewable safety lease. */
    public static @NotNull Optional<CitizenActivityReservation> reserveActivity(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull String ownerId
    ) {
        return reserveActivity(citizen, ownerId, Duration.ofMinutes(10));
    }

    /** Reserves a citizen for addon gameplay with an explicit renewable safety lease. */
    public static @NotNull Optional<CitizenActivityReservation> reserveActivity(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull String ownerId,
            @NotNull Duration timeout
    ) {
        return TalkingColonistsApi.services().conversations().reserveActivity(citizen, ownerId, timeout);
    }

    /** Clears only the automatic-conversation cooldown. */
    public static void resetAutomaticCooldown(@NotNull AbstractEntityCitizen citizen) {
        TalkingColonistsApi.services().conversations().resetAutomaticCooldown(citizen);
    }

    /**
     * Opens caller-controlled floor management for meetings/councils.
     *
     * <p>Silent participants consume no provider capacity. {@code options} defines which addon AI
     * tools are available in each explicitly requested turn; core tools keep their normal policy.
     * Turn completion is delivered on the Minecraft server thread after audible playback terminates.
     * </p>
     */
    public static @NotNull ControlledConversationSession createControlledSession(
            @NotNull MinecraftServer server,
            @NotNull List<AbstractEntityCitizen> participants,
            @NotNull String agenda,
            @NotNull ControlledConversationOptions options
    ) {
        return TalkingColonistsApi.services().conversations().createControlledSession(server, participants, agenda, options);
    }

    /** Creates an ordinary two-citizen autonomous conversation handle. */
    public static @NotNull CitizenConversationHandle createPairConversation(
            @NotNull MinecraftServer server,
            @NotNull AbstractEntityCitizen first,
            @NotNull AbstractEntityCitizen second
    ) {
        return TalkingColonistsApi.services().conversations().createPairConversation(server, first, second);
    }
}
