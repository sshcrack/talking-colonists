package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import me.sshcrack.mc_talking.commands.ListToolsCommand;
import me.sshcrack.mc_talking.conversations.CitizenConversation;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/*? if neoforge {*/
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/*?}*/
/*? if forge {*/
/*import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*/
/*?}*/
/*? if neoforge {*/
/*?}*/

/**
 * Handler for server-side events related to player-citizen interactions.
 */
public class ServerEventHandler {
    private int tickCounter = 0;

    /**
     * Called when the server starts
     */
    @SubscribeEvent
    public void onServerStart(ServerStartingEvent event) {
        if (!CONFIG.geminiApiKey.get().isEmpty()) {
            return;
        }

        McTalking.LOGGER.error("======================");
        McTalking.LOGGER.error("Gemini API key not set. McTalking is disabled.");
        McTalking.LOGGER.error("======================");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ListToolsCommand.register(event.getDispatcher());
    }

    /**
     * Called when the server stops
     */
    @SubscribeEvent
    public void onServerStop(ServerStoppingEvent event) {
        ConversationManager.cleanup();
    }

    /**
     * Called when an entity joins the level
     */
    @SubscribeEvent
    public void onPlayerJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        for (ItemStack item : player.getInventory().items) {
            if (item.getItem() instanceof CitizenTalkingDevice) {
                /*? if forge {*/
				/*CompoundTag tag = item.getOrCreateTag();
				tag.putInt("CustomModelData", 0);

				*//*?}*/
                /*? if neoforge {*/
                item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0));
                /*?}*/
            }
        }
    }

    /**
     * Called when an entity leaves the level
     */
    @SubscribeEvent
    public void onPlayerLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ConversationManager.endConversation(player.getUUID(), false);
        }
    }

    private void onServerTickCommon(MinecraftServer server) {
        tickCounter++;

        boolean doDistanceCheck = (tickCounter % 5 == 0);
        boolean doMumblingCheck = (tickCounter % CONFIG.mumblingCheckIntervalTicks.get() == 0);
        boolean doRandomConvCheck = CONFIG.enableCitizenToCitizenConversation.get()
                && CONFIG.enableRandomConversations.get()
                && (tickCounter % CONFIG.randomConversationCheckIntervalTicks.get() == 0);

        if (!doDistanceCheck && !doMumblingCheck && !doRandomConvCheck) {
            return;
        }

        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        // Collect players currently in a conversation so we can guard against
        // mumbles / random conversations interrupting them.
        var activePlayers = server.getPlayerList().getPlayers();

        for (ServerPlayer player : activePlayers) {
            UUID playerId = player.getUUID();

            if (doDistanceCheck) {
                UUID citizenId = ConversationManager.getPlayerConversationPartner(playerId);
                if (citizenId != null) {
                    checkConversationDistance(player, citizenId);
                }
            }

            // Mumbling: only if this player is NOT in a conversation
            if (doMumblingCheck && !ConversationManager.isPlayerInConversation(playerId)) {
                checkForMumblingCitizens(player);
            }
        }

        // Random citizen-to-citizen conversations (once per interval, not per player)
        if (doRandomConvCheck) {
            checkForRandomConversations(server);
        }
    }

    /**
     * Called every server tick
     */
    @SubscribeEvent
            /*? if forge {*/
	/*public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		onServerTickCommon(event.getServer());
	}
	*//*?}*/
            /*? if neoforge {*/
    public void onServerTick(ServerTickEvent.Post event) {
        onServerTickCommon(event.getServer());
    }
    /*?}*/

    private void checkConversationDistance(ServerPlayer player, UUID citizenId) {
        AbstractEntityCitizen activeEntity = ConversationManager.getActiveEntityForPlayer(player.getUUID());
        if (activeEntity == null || !activeEntity.isAlive()) {
            ConversationManager.endConversation(player.getUUID(), false);
            return;
        }

        double distanceSquared = player.distanceToSqr(activeEntity);
        if (distanceSquared > CONFIG.maxConversationDistance.get() * CONFIG.maxConversationDistance.get()) {
            ConversationManager.endConversation(player.getUUID(), true);
        }
    }

    private void checkForMumblingCitizens(ServerPlayer player) {
        double range = CONFIG.mumblingDetectionRange.get();
        var aabb = player.getBoundingBox().inflate(range);
        var citizens = player.level().getEntitiesOfClass(AbstractEntityCitizen.class, aabb);

        // If ANY citizen in range is already in a session, skip mumbling entirely for
        // this player this tick – two conversations near the same player at once is jarring.
        boolean anyBusy = citizens.stream()
                .anyMatch(c -> ConversationManager.isCitizenBusy(c.getUUID()));
        if (anyBusy) return;

        for (AbstractEntityCitizen citizen : citizens) {
            if (citizen instanceof VisitorCitizen) continue;
            if(citizen.isSleeping()) continue;
            // Skip if this citizen already has any kind of active session
            if (ConversationManager.isCitizenBusy(citizen.getUUID())) continue;
            // Skip if this citizen is still within their post-session cooldown
            if (ConversationManager.isCitizenOnCooldown(citizen.getUUID())) continue;
            if (Math.random() < CONFIG.mumblingChance.get()) {
                ConversationManager.startMumbling(citizen);
                break; // Only trigger one mumbling citizen per player per check
            }
        }
    }

    /**
     * Scans for pairs of nearby citizens that are not currently managed and
     * randomly starts a citizen-to-citizen conversation between them.
     *
     * <p>Only citizens within range of an online player are considered — citizens
     * in unloaded or player-free areas would be inaudible anyway, and scanning the
     * entire world on every check is expensive on large servers.</p>
     *
     * <p>Citizens that are currently talking to a player are excluded to avoid
     * interrupting ongoing player conversations.</p>
     */
    private void checkForRandomConversations(MinecraftServer server) {
        if (CONFIG.geminiApiKey.get().isEmpty()) return;

        double range = CONFIG.mumblingDetectionRange.get() * 2;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var nearbyBox = player.getBoundingBox().inflate(range);
            var citizens = player.serverLevel().getEntitiesOfClass(AbstractEntityCitizen.class, nearbyBox);

            // If ANY citizen in range is already in a session, skip starting a new
            // conversation near this player – overlapping sessions are disruptive.
            boolean anyBusy = citizens.stream()
                    .anyMatch(c -> ConversationManager.isCitizenBusy(c.getUUID()));
            if (anyBusy) continue;

            for (AbstractEntityCitizen citizen : citizens) {
                if (citizen instanceof VisitorCitizen) continue;
                // Skip if this citizen already has any kind of active session
                if (ConversationManager.isCitizenBusy(citizen.getUUID())) continue;
                // Skip if this citizen is still within their post-session cooldown
                if (ConversationManager.isCitizenOnCooldown(citizen.getUUID())) continue;
                if (citizen.isSleeping()) continue;

                if (Math.random() >= CONFIG.randomConversationChance.get()) continue;

                // Find a nearby partner from the same proximity list (no second query needed)
                List<AbstractEntityCitizen> partners = new ArrayList<>();
                for (AbstractEntityCitizen candidate : citizens) {
                    if (candidate == citizen) continue;
                    if (candidate instanceof VisitorCitizen) continue;
                    if (candidate.isSleeping()) continue;
                    // Skip if this candidate already has any kind of active session
                    if (ConversationManager.isCitizenBusy(candidate.getUUID())) continue;
                    // Skip if this candidate is still within their post-session cooldown
                    if (ConversationManager.isCitizenOnCooldown(candidate.getUUID())) continue;
                    partners.add(candidate);
                }

                if (partners.isEmpty()) continue;

                // Pick a random partner
                AbstractEntityCitizen partner = partners.get((int) (Math.random() * partners.size()));

                McTalking.LOGGER.info("[RandomConv] Starting conversation between {} and {}",
                        citizen.getCitizenData().getName(), partner.getCitizenData().getName());

                var conversation = new CitizenConversation(server, List.of(citizen, partner));
                conversation.setOnStateChanged(newState -> {
                    AiStatus status = switch (newState) {
                        case GENERATING -> AiStatus.THINKING;
                        case PLAYING_AUDIO -> AiStatus.IN_CONVERSATION;
                        case ENDED -> AiStatus.NONE;
                    };
                    AiStatusHelper.setAiStatusSynced(citizen, status);
                    AiStatusHelper.setAiStatusSynced(partner, status);
                });
                conversation.performConversation();

                // Only start one random conversation per check to keep API usage bounded
                return;
            }
        }
    }
}
