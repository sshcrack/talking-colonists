package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.manager.TalkingManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.*;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(MinecoloniesTalkingCitizens.MODID)
public class MinecoloniesTalkingCitizens {
    public static HashMap<UUID, TalkingManager> clients = new HashMap<>();
    public static HashMap<UUID, LivingEntity> activeEntity = new HashMap<>();

    // Track which entity each player is looking at
    private final Map<UUID, UUID> playerLookingAt = new HashMap<>();
    // Track how long each player has been looking at an entity (in ticks)
    private final Map<UUID, Integer> lookDuration = new HashMap<>();
    // Track the previous entity that was looked at for tolerance handling
    private final Map<UUID, UUID> previousEntityLookedAt = new HashMap<>();
    // Track when the last entity switch occurred
    private final Map<UUID, Long> lastEntitySwitchTime = new HashMap<>();

    private Queue<UUID> addedEntities = new LinkedList<>();

    // Configuration values loaded from Config class

    public static final String MODID = "mc_talking";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecoloniesTalkingCitizens(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    @SubscribeEvent
    private void onServerStart(ServerStartingEvent event) {
        if (!Config.geminiApiKey.isEmpty())
            return;

        LOGGER.error("======================");
        LOGGER.error("Gemini API key not set. Minecolonies Talking Citizens is disabled.");
        LOGGER.error("======================");
    }


    @SubscribeEvent
    private void onPlayerJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        if (Config.geminiApiKey.isEmpty()) {
            player.sendSystemMessage(
                    Component.literal("No Gemini API key set. Minecolonies Talking Citizens is disabled.")
                            .withStyle(ChatFormatting.RED)
            );
        }
    }

    @SubscribeEvent
    private void onServerShutdown(ServerStoppingEvent event) {
        for (var client : clients.values()) {
            client.close();
        }

        clients.clear();
    }

    int tick = 0;

    @SubscribeEvent
    private void onWorldTick(ServerTickEvent event) {
        if (vcApi == null || Config.geminiApiKey.isEmpty())
            return;

        if (tick++ % 5 != 0)
            return;

        var server = event.getServer();
        var players = new ArrayList<>(server.getPlayerList().getPlayers());

        for (ServerPlayer player : players) {
            if (player.isSpectator())
                continue;

            UUID playerId = player.getUUID();

            // Find the entity the player is looking at
            LivingEntity currentTargetEntity = findEntityPlayerLookingAt(player);
            UUID currentTargetId = currentTargetEntity != null ? currentTargetEntity.getUUID() : null;
            UUID previousTargetId = playerLookingAt.get(playerId);

            // No entity found
            if (currentTargetId == null) {
                resetPlayerLookTracking(playerId);
                continue;
            }

            // Check if player is looking at a new entity
            if (previousTargetId == null || !previousTargetId.equals(currentTargetId)) {
                handleNewTargetEntity(playerId, currentTargetId, previousTargetId);
                continue;
            }

            // Player is still looking at the same entity
            int currentDuration = lookDuration.getOrDefault(playerId, 0) + 1;
            lookDuration.put(playerId, currentDuration);

            // Check if the player has been looking long enough
            if (currentDuration >= Config.lookDurationTicks) {
                // This is where we confirm the player has been looking at the entity for the required duration
                if (activeEntity.get(playerId) == null || !activeEntity.get(playerId).getUUID().equals(currentTargetId)) {
                    // If there was a previously focused entity, remove its glowing effect
                    LivingEntity previousEntity = activeEntity.get(playerId);
                    if (previousEntity != null && previousEntity.isAlive()) {
                        previousEntity.removeEffect(MobEffects.GLOWING);
                    }

                    // Set new active entity and apply glowing
                    activeEntity.put(playerId, currentTargetEntity);
                    // Apply glowing effect (infinite duration with showIcon=false)
                    currentTargetEntity.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false));
                    LOGGER.info("Player {} is now focusing on entity {}", player.getName().getString(), currentTargetEntity);

                    clients.put(currentTargetId, new TalkingManager(currentTargetEntity));

                    addedEntities.add(currentTargetId);
                    if (addedEntities.size() > Config.maxConcurrentAgents) {
                        var u = addedEntities.poll();
                        if (u != null) {
                            var m = clients.get(u);
                            if (m != null) {
                                m.close();
                                clients.remove(u);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Find the entity a player is currently looking at
     *
     * @param player The player to check
     * @return The entity the player is looking at, or null if none
     */
    private LivingEntity findEntityPlayerLookingAt(ServerPlayer player) {
        var level = player.level();
        return level.getNearestEntity(
                AbstractEntityCitizen.class,
                TargetingConditions.forNonCombat()
                        .range(Config.activationDistance),
                player,
                player.getX(), player.getY(), player.getZ(),
                player.getBoundingBox().inflate(Config.activationDistance)
        );
    }

    /**
     * Handle when a player starts looking at a new entity
     *
     * @param playerId         Player UUID
     * @param currentTargetId  Current target entity UUID
     * @param previousTargetId Previous target entity UUID
     */
    private void handleNewTargetEntity(UUID playerId, UUID currentTargetId, UUID previousTargetId) {
        long currentTime = System.currentTimeMillis();

        // Check if it's a return to the previous entity within tolerance time
        UUID previousEntityId = previousEntityLookedAt.get(playerId);
        if (previousEntityId != null && previousEntityId.equals(currentTargetId)) {
            // If we're returning to the previous entity within tolerance period
            long lastSwitchTime = lastEntitySwitchTime.getOrDefault(playerId, 0L);
            if (currentTime - lastSwitchTime < Config.lookToleranceMs) {
                // We came back to the previous entity quickly, restore previous duration
                // but with a small penalty
                int previousDuration = lookDuration.getOrDefault(playerId, 0);
                lookDuration.put(playerId, Math.max(0, previousDuration - 5));
                playerLookingAt.put(playerId, currentTargetId);
                return;
            }
        }

        // This is a new entity or we exceeded tolerance time
        playerLookingAt.put(playerId, currentTargetId);
        previousEntityLookedAt.put(playerId, previousTargetId);
        lastEntitySwitchTime.put(playerId, currentTime);
        lookDuration.put(playerId, 0);

        // If we were tracking an active entity, remove its glowing effect and clear it
        LivingEntity previousActiveEntity = activeEntity.get(playerId);
        if (previousActiveEntity != null && previousActiveEntity.isAlive()) {
            previousActiveEntity.removeEffect(MobEffects.GLOWING);
        }

        activeEntity.remove(playerId);
    }

    /**
     * Reset tracking data when a player stops looking at entities
     *
     * @param playerId Player UUID
     */
    private void resetPlayerLookTracking(UUID playerId) {
        UUID previousTargetId = playerLookingAt.get(playerId);
        if (previousTargetId != null) {
            previousEntityLookedAt.put(playerId, previousTargetId);
            lastEntitySwitchTime.put(playerId, System.currentTimeMillis());
        }

        // Remove glowing effect from any active entity
        LivingEntity entity = activeEntity.get(playerId);
        if (entity != null && entity.isAlive()) {
            entity.removeEffect(MobEffects.GLOWING);
        }

        playerLookingAt.remove(playerId);
        lookDuration.remove(playerId);
        activeEntity.remove(playerId);
    }
}
