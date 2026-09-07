package me.sshcrack.mc_talking.api;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.conversation.AmbientLineResult;
import me.sshcrack.mc_talking.api.conversation.CitizenActivityReservation;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationHandle;
import me.sshcrack.mc_talking.api.conversation.CitizenSpeechPolicy;
import me.sshcrack.mc_talking.api.conversation.CitizenUrgencyModifier;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationOptions;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.conversation.ConversationStartResult;
import me.sshcrack.mc_talking.api.conversation.ConversationLifecycleListener;
import me.sshcrack.mc_talking.api.conversation.ConversationEligibility;
import me.sshcrack.mc_talking.api.memory.AddonConfirmedOutcome;
import me.sshcrack.mc_talking.api.memory.AddonMemoryWriteResult;
import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;
import me.sshcrack.mc_talking.api.pregen.PregenerationPromptModifier;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptContributor;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.tool.AiTool;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Root entry point for the Talking Colonists addon API.
 *
 * <p>The normal Talking Colonists mod contains the runtime implementation. The separate
 * {@code mc_talking-api} artifact is only a compile/source surface for addon developers and must
 * not be installed as an additional mod.</p>
 */
public final class TalkingColonistsApi {
    /** Breaking API generation for addon compatibility declarations. */
    public static final int API_MAJOR_VERSION = 2;

    private static final String IMPLEMENTATION_CLASS =
            "me.sshcrack.mc_talking.internal.api.TalkingColonistsApiBackend";
    private static volatile Services services;

    private TalkingColonistsApi() {
    }

    /** Returns whether the normal Talking Colonists runtime is present and exposes this API. */
    public static boolean isAvailable() {
        try {
            return resolveServices() != null;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    /** Returns the major API generation implemented by the installed normal mod. */
    public static int runtimeApiMajorVersion() {
        return services().apiMajorVersion();
    }

    /**
     * Returns the runtime service surface supplied by the installed Talking Colonists mod.
     *
     * <p>Most addons can use the focused static facade classes instead. This unified service view
     * is useful for frameworks/integration layers that prefer dependency injection.</p>
     */
    public static @NotNull Services services() {
        Services current = resolveServices();
        if (current == null) {
            throw new IllegalStateException("Talking Colonists runtime is not available");
        }
        return current;
    }

    private static Services resolveServices() {
        Services current = services;
        if (current != null) return current;
        synchronized (TalkingColonistsApi.class) {
            current = services;
            if (current != null) return current;
            try {
                Class<?> implementation = Class.forName(
                        IMPLEMENTATION_CLASS,
                        true,
                        TalkingColonistsApi.class.getClassLoader()
                );
                Object instance = implementation.getField("INSTANCE").get(null);
                if (!(instance instanceof Services resolved)) {
                    throw new IllegalStateException(
                            "Installed Talking Colonists runtime does not implement the expected addon API services"
                    );
                }
                services = resolved;
                return resolved;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Talking Colonists runtime is not available; install the normal Talking Colonists mod",
                        e
                );
            }
        }
    }

    /**
     * Unified supported runtime surface. Addons consume this interface; only Talking Colonists
     * supplies its implementation.
     */
    public interface Services {
        int apiMajorVersion();

        @NotNull AddonRegistration registerPromptProvider(
                @NotNull String id,
                int priority,
                @NotNull CitizenPromptProvider provider
        );

        @NotNull AddonRegistration registerPromptContributor(
                @NotNull String id,
                int order,
                @NotNull CitizenPromptContributor contributor
        );

        @NotNull AddonRegistration registerSpeechPolicy(
                @NotNull String id,
                int order,
                @NotNull CitizenSpeechPolicy policy
        );

        @NotNull AddonRegistration registerUrgencyModifier(
                @NotNull String id,
                int order,
                @NotNull CitizenUrgencyModifier modifier
        );

        @NotNull AddonRegistration registerPregenerationPromptModifier(
                @NotNull String id,
                int order,
                @NotNull PregenerationPromptModifier modifier
        );

        @NotNull AddonRegistration registerAiTool(
                @NotNull String namespace,
                @NotNull String name,
                @NotNull AiTool tool
        );

        @NotNull CitizenPromptView snapshotCitizenContext(
                @NotNull AbstractEntityCitizen citizen,
                @Nullable ServerPlayer speakingPlayer
        );

        boolean isBusy(@NotNull AbstractEntityCitizen citizen);

        @NotNull ConversationEligibility conversationEligibility(
                @NotNull AbstractEntityCitizen citizen,
                @NotNull ConversationKind kind
        );

        @NotNull ConversationStartResult startPlayerConversation(
                @NotNull ServerPlayer player,
                @NotNull AbstractEntityCitizen citizen
        );

        @NotNull CompletableFuture<AmbientLineResult> requestAmbientLine(
                @NotNull AbstractEntityCitizen citizen,
                @NotNull String promptDirective
        );

        @NotNull AddonRegistration registerConversationLifecycleListener(
                @NotNull String id,
                int order,
                @NotNull ConversationLifecycleListener listener
        );

        @NotNull Optional<ConversationKind> activeConversationKind(@NotNull AbstractEntityCitizen citizen);

        @NotNull Optional<UUID> activePlayerId(@NotNull AbstractEntityCitizen citizen);

        boolean isPlayerInConversation(@NotNull ServerPlayer player);

        boolean hasAmbientCapacity(int slotsNeeded);

        boolean hasPlayerNearby(@NotNull AbstractEntityCitizen citizen, double range);

        boolean requestGracefulEnd(@NotNull AbstractEntityCitizen citizen);

        @NotNull Optional<CitizenActivityReservation> reserveActivity(
                @NotNull AbstractEntityCitizen citizen,
                @NotNull String ownerId,
                @NotNull Duration timeout
        );

        void resetAutomaticCooldown(@NotNull AbstractEntityCitizen citizen);

        @NotNull ControlledConversationSession createControlledSession(
                @NotNull MinecraftServer server,
                @NotNull List<AbstractEntityCitizen> participants,
                @NotNull String agenda,
                @NotNull ControlledConversationOptions options
        );

        @NotNull CitizenConversationHandle createPairConversation(
                @NotNull MinecraftServer server,
                @NotNull AbstractEntityCitizen first,
                @NotNull AbstractEntityCitizen second
        );

        boolean addMemoryEvent(@NotNull ICitizenData citizen, @NotNull String event);

        boolean removeMemoryEvent(@NotNull ICitizenData citizen, @NotNull String event);

        boolean addMemoryFact(@NotNull ICitizenData citizen, @NotNull String fact);

        boolean removeMemoryFact(@NotNull ICitizenData citizen, @NotNull String fact);

        boolean addMemoryRelationshipChange(
                @NotNull ICitizenData citizen,
                @NotNull UUID targetId,
                @NotNull CitizenRelationshipDimension dimension,
                float delta
        );

        @NotNull AddonMemoryWriteResult confirmMemoryOutcome(
                @NotNull ICitizenData citizen,
                @NotNull AddonConfirmedOutcome outcome
        );

        @NotNull Optional<CitizenMemorySnapshot> memorySnapshot(@NotNull ICitizenData citizen);
    }
}
