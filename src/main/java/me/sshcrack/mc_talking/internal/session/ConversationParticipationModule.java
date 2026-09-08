package me.sshcrack.mc_talking.internal.session;

import me.sshcrack.mc_talking.network.AiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Owns the provider/participation facts used to decide whether a citizen can actually hear a player.
 * Minecraft thread synchronization and status application live outside this module.
 */
public interface ConversationParticipationModule {
    enum ProviderReadiness {
        NOT_READY,
        CONNECTING,
        READY,
        RECOVERING
    }

    enum AudibleActivity {
        IDLE,
        THINKING,
        TALKING
    }

    record Ownership(ForegroundSessionRegistry.Token token, @Nullable UUID directPlayerId) {
    }

    @FunctionalInterface
    interface OwnershipView {
        @Nullable Ownership current(UUID citizenId);
    }

    void register(ForegroundSessionRegistry.Token token);

    void providerConnecting(ForegroundSessionRegistry.Token token);

    void providerReady(ForegroundSessionRegistry.Token token);

    void providerRecovering(ForegroundSessionRegistry.Token token);

    void playbackThinking(ForegroundSessionRegistry.Token token);

    void playbackTalking(ForegroundSessionRegistry.Token token);

    void playbackIdle(ForegroundSessionRegistry.Token token);

    void inputAwaitingResponse(ForegroundSessionRegistry.Token token, boolean active);

    void urgentWalking(ForegroundSessionRegistry.Token token, boolean active);

    void failure(ForegroundSessionRegistry.Token token, AiStatus status);

    Optional<AiStatus> presentationIfCurrent(ForegroundSessionRegistry.Token token);

    boolean canRouteInput(ForegroundSessionRegistry.Token token, UUID playerId);

    void complete(ForegroundSessionRegistry.Token token);

    boolean canClearAfterCompletion(ForegroundSessionRegistry.Token token);
}
