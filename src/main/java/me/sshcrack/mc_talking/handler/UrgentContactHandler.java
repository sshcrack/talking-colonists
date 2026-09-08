package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.internal.session.UrgentContactLifecycleModule;
import me.sshcrack.mc_talking.util.CitizenNeedAssessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Minecraft entry points for citizen-initiated urgent contact.
 *
 * <p>Journey state itself lives in {@link UrgentContactLifecycleModule}; this handler only chooses
 * candidates, adapts Minecraft objects, and preserves the existing per-player automatic-contact
 * cooldown policy.</p>
 */
public class UrgentContactHandler {
    private UrgentContactHandler() {
    }

    private static final UrgentContactLifecycleModule lifecycle = new UrgentContactLifecycleModule(
            System::nanoTime,
            new UrgentContactLifecycleModule.Timing(
                    TimeUnit.SECONDS.toNanos(60),
                    TimeUnit.SECONDS.toNanos(1)
            )
    );

    public static void checkForCitizenInitiatedContact(
            ServerPlayer player,
            List<AbstractEntityCitizen> citizens,
            Set<UUID> contactedThisInterval
    ) {
        if (!McTalkingConfig.hasGeminiApiKey()) return;

        int playerCooldownSecs = McTalkingConfig.INSTANCE.instance().playerUrgentContactCooldownSeconds;
        if (playerCooldownSecs > 0 && lifecycle.isPlayerOnCooldown(
                player.getUUID(), TimeUnit.SECONDS.toNanos(playerCooldownSecs))) {
            return;
        }

        double baseChance = McTalkingConfig.INSTANCE.instance().citizenContactBaseChance;
        boolean walkToPlayer = McTalkingConfig.INSTANCE.instance().enableUrgentContactWalkToPlayer;

        List<AbstractEntityCitizen> contactCitizens = citizens;
        if (walkToPlayer) {
            double wideRange = McTalkingConfig.INSTANCE.instance().urgentContactSearchRange;
            var wideAabb = player.getBoundingBox().inflate(wideRange);
            contactCitizens = player.level().getEntitiesOfClass(AbstractEntityCitizen.class, wideAabb);
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;
        MinecraftUrgentContactAdapter adapter = new MinecraftUrgentContactAdapter(server);

        for (AbstractEntityCitizen citizen : contactCitizens) {
            if (!ConversationManager.canCitizenSpeak(citizen, ConversationKind.URGENT_CONTACT)) continue;
            if (citizen.getCitizenData() == null) continue;
            if (lifecycle.isActive(citizen.getUUID())) continue;
            if (!contactedThisInterval.add(citizen.getUUID())) continue;

            double urgencyWeight = CitizenNeedAssessor.calculateUrgencyWeight(citizen);
            if (urgencyWeight <= 0) continue;

            if (Math.random() < baseChance * urgencyWeight) {
                McTalking.LOGGER.info("[CitizenContact] Citizen {} initiating {} with player {}",
                        citizen.getCitizenData().getName(),
                        walkToPlayer ? "walk-to-player" : "contact",
                        player.getName().getString());

                UrgentContactLifecycleModule.StartResult result = walkToPlayer
                        ? lifecycle.startWalking(citizen.getUUID(), player.getUUID(), adapter,
                        terminal -> logTerminal(citizen.getUUID(), terminal))
                        : lifecycle.startAnnouncement(citizen.getUUID(), player.getUUID(), adapter,
                        terminal -> logTerminal(citizen.getUUID(), terminal));
                if (result.started()) {
                    // Preserve the previous automatic-contact cooldown behavior: a successful walk
                    // reservation consumes it immediately, while an immediate announcement startup
                    // failure does not. This also prevents arrival/startup failures from retry-looping.
                    lifecycle.recordPlayerContact(player.getUUID());
                    break;
                }
            }
        }
    }

    public static boolean triggerWalkToPlayer(AbstractEntityCitizen citizen, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        var result = lifecycle.startWalking(
                citizen.getUUID(), player.getUUID(), new MinecraftUrgentContactAdapter(server),
                terminal -> logTerminal(citizen.getUUID(), terminal));
        return result.started();
    }

    /** Advances walking, announcement cancellation, invalidation, and ownership handoff checks. */
    public static void tick(MinecraftServer server) {
        lifecycle.tick(new MinecraftUrgentContactAdapter(server));
    }

    /** Called only after a normal direct conversation has successfully taken responsibility. */
    public static void onPlayerTakeover(AbstractEntityCitizen citizen, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        lifecycle.onPlayerTakeover(citizen.getUUID(), player.getUUID(), new MinecraftUrgentContactAdapter(server));
    }

    public static void onPlayerLeave(UUID playerId, MinecraftServer server) {
        if (server == null) return;
        lifecycle.onPlayerDeparture(playerId, new MinecraftUrgentContactAdapter(server));
    }

    public static void onServerStop(MinecraftServer server) {
        lifecycle.shutdown(new MinecraftUrgentContactAdapter(server));
    }

    private static void logTerminal(UUID citizenId, UrgentContactLifecycleModule.TerminalContact terminal) {
        switch (terminal.outcome()) {
            case COMPLETED, TAKEN_OVER -> McTalking.LOGGER.info(
                    "[CitizenContact] Citizen {} contact {} ended: {} ({})",
                    citizenId, terminal.contactId(), terminal.outcome(), terminal.detail());
            default -> McTalking.LOGGER.debug(
                    "[CitizenContact] Citizen {} contact {} ended: {} ({})",
                    citizenId, terminal.contactId(), terminal.outcome(), terminal.detail());
        }
    }
}
