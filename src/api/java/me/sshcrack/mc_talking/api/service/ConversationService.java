package me.sshcrack.mc_talking.api.service;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.conversation.*;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Runtime service for addon-facing conversation control and lifecycle observation. */
public interface ConversationService {
    boolean isBusy(@NotNull AbstractEntityCitizen citizen);
    @NotNull ConversationEligibility eligibility(@NotNull AbstractEntityCitizen citizen, @NotNull ConversationKind kind);
    @NotNull ConversationStartResult startPlayerConversation(@NotNull ServerPlayer player, @NotNull AbstractEntityCitizen citizen);
    @NotNull CompletableFuture<AmbientLineResult> requestAmbientLine(@NotNull AbstractEntityCitizen citizen, @NotNull String promptDirective);
    @NotNull AddonRegistration registerLifecycleListener(@NotNull String id, int order, @NotNull ConversationLifecycleListener listener);
    @NotNull Optional<ConversationKind> activeKind(@NotNull AbstractEntityCitizen citizen);
    @NotNull Optional<UUID> activePlayerId(@NotNull AbstractEntityCitizen citizen);
    boolean isPlayerInConversation(@NotNull ServerPlayer player);
    boolean hasAmbientCapacity(int slotsNeeded);
    boolean hasPlayerNearby(@NotNull AbstractEntityCitizen citizen, double range);
    boolean requestGracefulEnd(@NotNull AbstractEntityCitizen citizen);
    @NotNull Optional<CitizenActivityReservation> reserveActivity(@NotNull AbstractEntityCitizen citizen,
                                                                   @NotNull String ownerId,
                                                                   @NotNull Duration timeout);
    void resetAutomaticCooldown(@NotNull AbstractEntityCitizen citizen);
    @NotNull ControlledConversationSession createControlledSession(@NotNull MinecraftServer server,
                                                                    @NotNull List<AbstractEntityCitizen> participants,
                                                                    @NotNull String agenda,
                                                                    @NotNull ControlledConversationOptions options);
    @NotNull CitizenConversationHandle createPairConversation(@NotNull MinecraftServer server,
                                                               @NotNull AbstractEntityCitizen first,
                                                               @NotNull AbstractEntityCitizen second);
}
