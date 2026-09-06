package me.sshcrack.mc_talking.api;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.conversation.CitizenActivityReservation;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationHandle;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point shared by the standalone addon API artifact and the full Talking Colonists mod.
 *
 * <p>Addons consume the public services under {@code me.sshcrack.mc_talking.api}. The full mod
 * installs the backend implementation during startup; the backend itself lives only in the normal
 * mod source set and is intentionally absent from the addon API jar.</p>
 */
public final class TalkingColonistsApi {
    private static volatile Backend backend;

    private TalkingColonistsApi() {
    }

    /** Returns whether the full Talking Colonists runtime has installed its API backend. */
    public static boolean isAvailable() {
        return backend != null;
    }

    /**
     * Core-only bootstrap hook. Addons must not install or replace the backend.
     */
    @ApiStatus.Internal
    public static synchronized void installBackend(@NotNull Backend implementation) {
        if (backend != null && backend != implementation) {
            throw new IllegalStateException("Talking Colonists API backend is already installed");
        }
        backend = java.util.Objects.requireNonNull(implementation, "implementation");
    }

    /** Core-only backend accessor used by public facade classes. */
    @ApiStatus.Internal
    public static @NotNull Backend backend() {
        Backend current = backend;
        if (current == null) {
            throw new IllegalStateException("Talking Colonists core is not initialized");
        }
        return current;
    }

    /**
     * SPI implemented by the full mod. It deliberately exposes only public API/external types so
     * the standalone API artifact never needs implementation classes on an addon's compile classpath.
     */
    @ApiStatus.Internal
    public interface Backend {
        @NotNull CitizenPromptProvider defaultPromptProvider();

        boolean isBusy(@NotNull AbstractEntityCitizen citizen);

        boolean canSpeak(@NotNull AbstractEntityCitizen citizen, @NotNull ConversationKind kind);

        boolean startPlayerConversation(@NotNull ServerPlayer player, @NotNull AbstractEntityCitizen citizen);

        boolean startAmbientLine(@NotNull AbstractEntityCitizen citizen, @NotNull String promptDirective);

        @NotNull Optional<UUID> activePlayerId(@NotNull AbstractEntityCitizen citizen);

        boolean isPlayerInConversation(@NotNull ServerPlayer player);

        boolean hasAmbientCapacity(int slotsNeeded);

        boolean hasPlayerNearby(@NotNull AbstractEntityCitizen citizen, double range);

        boolean requestGracefulEnd(@NotNull AbstractEntityCitizen citizen);

        @NotNull Optional<CitizenActivityReservation> reserveActivity(
                @NotNull AbstractEntityCitizen citizen,
                @NotNull String ownerId
        );

        void resetAutomaticCooldown(@NotNull AbstractEntityCitizen citizen);

        @NotNull ControlledConversationSession createControlledSession(
                @NotNull MinecraftServer server,
                @NotNull List<AbstractEntityCitizen> participants,
                @NotNull String agenda
        );

        @NotNull CitizenConversationHandle createPairConversation(
                @NotNull MinecraftServer server,
                @NotNull AbstractEntityCitizen first,
                @NotNull AbstractEntityCitizen second
        );

        boolean addMemoryEvent(@NotNull ICitizenData citizen, @NotNull String event);

        boolean addMemoryFact(@NotNull ICitizenData citizen, @NotNull String fact);

        @NotNull Optional<CitizenMemorySnapshot> memorySnapshot(@NotNull ICitizenData citizen);
    }
}
