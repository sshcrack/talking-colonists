package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.api.conversation.AmbientLineResult;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.internal.session.ForegroundSessionRegistry;
import me.sshcrack.mc_talking.internal.session.UrgentContactLifecycleModule;
import me.sshcrack.mc_talking.util.CitizenHelper;
import me.sshcrack.mc_talking.util.CitizenNeedAssessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Minecraft/navigation/provider adapter for the deterministic urgent-contact lifecycle module. */
final class MinecraftUrgentContactAdapter implements UrgentContactLifecycleModule.Adapter {
    private final MinecraftServer server;

    MinecraftUrgentContactAdapter(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    @Nullable
    public UrgentContactLifecycleModule.Reservation reserve(UUID citizenId) {
        AbstractEntityCitizen citizen = citizen(citizenId);
        if (citizen == null || !ConversationManager.canCitizenSpeak(citizen, ConversationKind.URGENT_CONTACT)) {
            return null;
        }
        ConversationManager.ForegroundReservation reservation =
                ConversationManager.reserveAmbientForeground(citizen, ConversationKind.URGENT_CONTACT);
        return reservation == null ? null : new ReservationAdapter(reservation);
    }

    @Override
    public boolean playerValid(UUID playerId) {
        ServerPlayer player = player(playerId);
        return player != null && player.isAlive() && !player.isRemoved();
    }

    @Override
    public boolean citizenValid(UUID citizenId) {
        AbstractEntityCitizen citizen = citizen(citizenId);
        return citizen != null
                && citizen.isAlive()
                && !citizen.isRemoved()
                && citizen.getCitizenData() != null;
    }

    @Override
    public boolean targetContextValid(UUID citizenId, UUID playerId) {
        AbstractEntityCitizen citizen = citizen(citizenId);
        ServerPlayer player = player(playerId);
        return citizen != null && player != null && citizen.level() == player.level();
    }

    @Override
    public boolean urgentNeedPresent(UUID citizenId) {
        AbstractEntityCitizen citizen = citizen(citizenId);
        return citizen != null && CitizenNeedAssessor.calculateUrgencyWeight(citizen) > 0;
    }

    @Override
    public boolean arrived(UUID citizenId, UUID playerId) {
        AbstractEntityCitizen citizen = citizen(citizenId);
        ServerPlayer player = player(playerId);
        if (citizen == null || player == null || citizen.level() != player.level()) return false;
        double range = McTalkingConfig.INSTANCE.instance().citizenInteractionRange;
        return citizen.distanceToSqr(player) <= range * range;
    }

    @Override
    @Nullable
    public UUID directPlayer(UUID citizenId) {
        return ConversationManager.getPlayerForEntity(citizenId);
    }

    @Override
    public void navigateTo(UUID citizenId, UUID playerId) {
        AbstractEntityCitizen citizen = citizen(citizenId);
        ServerPlayer player = player(playerId);
        if (citizen == null || player == null) return;
        citizen.getNavigation().moveTo(player, McTalkingConfig.CITIZEN_URGENT_WALK_SPEED);
    }

    @Override
    public void stopNavigation(UUID citizenId) {
        AbstractEntityCitizen citizen = citizen(citizenId);
        if (citizen != null && citizen.isAlive()) citizen.getNavigation().stop();
    }

    @Override
    public boolean startAnnouncement(
            UrgentContactLifecycleModule.Snapshot contact,
            UrgentContactLifecycleModule.Reservation reservation,
            Consumer<UrgentContactLifecycleModule.AnnouncementResult> completion
    ) {
        if (!(reservation instanceof ReservationAdapter owned)) return false;
        AbstractEntityCitizen citizen = citizen(contact.citizenId());
        ServerPlayer originPlayer = player(contact.originPlayerId());
        if (citizen == null || originPlayer == null) return false;

        return ConversationManager.startUrgentAnnouncement(
                owned.delegate,
                citizen,
                originPlayer.getName().getString(),
                result -> completion.accept(toLifecycleResult(result))
        );
    }

    private static UrgentContactLifecycleModule.AnnouncementResult toLifecycleResult(AmbientLineResult result) {
        return switch (result.status()) {
            case COMPLETED -> UrgentContactLifecycleModule.AnnouncementResult.completed();
            case FAILED, REJECTED -> UrgentContactLifecycleModule.AnnouncementResult.failed(result.detail());
            case CANCELLED -> UrgentContactLifecycleModule.AnnouncementResult.cancelled(result.detail());
        };
    }

    @Nullable
    private AbstractEntityCitizen citizen(UUID citizenId) {
        return CitizenHelper.findCitizen(server, citizenId);
    }

    @Nullable
    private ServerPlayer player(UUID playerId) {
        return server.getPlayerList().getPlayer(playerId);
    }

    private static final class ReservationAdapter implements UrgentContactLifecycleModule.Reservation {
        private final ConversationManager.ForegroundReservation delegate;

        private ReservationAdapter(ConversationManager.ForegroundReservation delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isCurrent() {
            return delegate.isCurrent();
        }

        @Override
        public void urgentWalking(boolean active) {
            delegate.markUrgentWalking(active);
        }

        @Override
        public boolean end(ForegroundSessionRegistry.TerminalReason reason, String detail) {
            return delegate.end(reason, detail);
        }
    }
}
