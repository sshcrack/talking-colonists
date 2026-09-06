package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Supported addon-facing conversation entry points. */
public final class CitizenConversationService {
    private CitizenConversationService() {
    }

    public static boolean isBusy(@NotNull AbstractEntityCitizen citizen) {
        return TalkingColonistsApi.backend().isBusy(citizen);
    }

    public static boolean canSpeak(@NotNull AbstractEntityCitizen citizen, @NotNull ConversationKind kind) {
        return TalkingColonistsApi.backend().canSpeak(citizen, kind);
    }

    public static boolean startPlayerConversation(@NotNull ServerPlayer player, @NotNull AbstractEntityCitizen citizen) {
        return TalkingColonistsApi.backend().startPlayerConversation(player, citizen);
    }

    /** Starts a one-sided addon-directed ambient line. */
    public static boolean startAmbientLine(@NotNull AbstractEntityCitizen citizen, @NotNull String promptDirective) {
        return TalkingColonistsApi.backend().startAmbientLine(citizen, promptDirective);
    }

    /** Returns the player currently speaking directly to this citizen, if any. */
    public static @NotNull Optional<UUID> activePlayerId(@NotNull AbstractEntityCitizen citizen) {
        return TalkingColonistsApi.backend().activePlayerId(citizen);
    }

    /** Returns whether the player currently owns a direct Talking Colonists conversation. */
    public static boolean isPlayerInConversation(@NotNull ServerPlayer player) {
        return TalkingColonistsApi.backend().isPlayerInConversation(player);
    }

    /** Returns true only for genuinely free low-priority provider capacity. */
    public static boolean hasAmbientCapacity(int slotsNeeded) {
        return TalkingColonistsApi.backend().hasAmbientCapacity(slotsNeeded);
    }

    /** Returns whether any online player in the same dimension is within {@code range} blocks. */
    public static boolean hasPlayerNearby(@NotNull AbstractEntityCitizen citizen, double range) {
        return TalkingColonistsApi.backend().hasPlayerNearby(citizen, range);
    }

    /** Requests the active citizen session to finish audible playback and close. */
    public static boolean requestGracefulEnd(@NotNull AbstractEntityCitizen citizen) {
        return TalkingColonistsApi.backend().requestGracefulEnd(citizen);
    }

    /** Reserves a citizen for addon gameplay with ownership-safe release semantics. */
    public static @NotNull Optional<CitizenActivityReservation> reserveActivity(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull String ownerId
    ) {
        return TalkingColonistsApi.backend().reserveActivity(citizen, ownerId);
    }

    /** Clears only the automatic-conversation cooldown. */
    public static void resetAutomaticCooldown(@NotNull AbstractEntityCitizen citizen) {
        TalkingColonistsApi.backend().resetAutomaticCooldown(citizen);
    }

    /** Opens caller-controlled floor management for meetings/councils. */
    public static @NotNull ControlledConversationSession createControlledSession(
            @NotNull MinecraftServer server,
            @NotNull List<AbstractEntityCitizen> participants,
            @NotNull String agenda
    ) {
        return TalkingColonistsApi.backend().createControlledSession(server, participants, agenda);
    }

    /** Creates an ordinary two-citizen autonomous conversation handle. */
    public static @NotNull CitizenConversationHandle createPairConversation(
            @NotNull MinecraftServer server,
            @NotNull AbstractEntityCitizen first,
            @NotNull AbstractEntityCitizen second
    ) {
        return TalkingColonistsApi.backend().createPairConversation(server, first, second);
    }
}
