package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import me.sshcrack.mc_talking.manager.AiTools;
import me.sshcrack.mc_talking.manager.TalkingManager;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import me.sshcrack.mc_talking.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import org.slf4j.Logger;

import java.util.*;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(MinecoloniesTalkingCitizens.MODID)
public class MinecoloniesTalkingCitizens {
    public static boolean isDedicated;

    public static HashMap<UUID, AiStatus> aiStatus = new HashMap<>();
    public static HashMap<UUID, TalkingManager> clients = new HashMap<>();
    public static HashMap<UUID, AbstractEntityCitizen> activeEntity = new HashMap<>();

    // Track which entity each player is looking at
    private final Map<UUID, UUID> playerLookingAt = new HashMap<>();
    // Track how long each player has been looking at an entity (in ticks)
    private final Map<UUID, Integer> lookDuration = new HashMap<>();
    // Track the previous entity that was looked at for tolerance handling
    private final Map<UUID, UUID> previousEntityLookedAt = new HashMap<>();
    // Track when the last entity switch occurred
    private final Map<UUID, Long> lastEntitySwitchTime = new HashMap<>();

    // Track player-entity conversation pairs for distance checking
    static final Map<UUID, UUID> playerConversationPartners = new HashMap<>();

    private static final Queue<UUID> addedEntities = new LinkedList<>();

    // Configuration values loaded from Config class

    public static final String MODID = "mc_talking";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MinecoloniesTalkingCitizens(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register items
        ModItems.register(modEventBus);
        ModAttachmentTypes.register(modEventBus);
        modEventBus.addListener(this::registerPayloadHandlers);
        AiTools.register();
    }

    /**
     * Add an entity to the managed entities queue
     *
     * @param entityId The UUID of the entity to add
     */
    public static void addEntity(UUID entityId) {
        addedEntities.add(entityId);
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

    /**
     * Start a conversation between a player and a citizen
     *
     * @param player  The player
     * @param citizen The citizen entity
     */
    public static void startConversation(ServerPlayer player, AbstractEntityCitizen citizen) {
        if (Config.geminiApiKey.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("mc_talking.no_key")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        UUID playerId = player.getUUID();
        UUID citizenId = citizen.getUUID();

        // Set citizen as active entity and add glowing effect
        activeEntity.put(playerId, citizen);

        // Create talking manager and add to clients
        clients.put(citizenId, new TalkingManager(citizen, player));
        addEntity(citizenId);

        // Track this conversation pair
        MinecoloniesTalkingCitizens
                .playerConversationPartners.put(playerId, citizenId);
    }

    /**
     * End a conversation for a specific player
     *
     * @param playerId    The player's UUID
     * @param sendMessage Whether to send a message to the player
     */
    public static void endConversation(UUID playerId, boolean sendMessage) {
        UUID citizenId = MinecoloniesTalkingCitizens.playerConversationPartners.remove(playerId);
        if (citizenId == null) return;

        LivingEntity entity = activeEntity.remove(playerId);
        if (entity != null && entity.isAlive()) {
            ServerPlayer player = entity.level().getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                for (ItemStack item : player.getInventory().items) {
                    if (item.getItem() instanceof CitizenTalkingDevice) {
                        item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0));
                    }
                }

                PacketDistributor.sendToPlayersTrackingEntity(entity, new AiStatusPayload(citizenId, AiStatus.NONE));
                PacketDistributor.sendToPlayer(player, new AiStatusPayload(citizenId, AiStatus.NONE));
                if (sendMessage) {
                    player.sendSystemMessage(Component.translatable("mc_talking.too_far")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
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


        for (ItemStack item : player.getInventory().items) {
            if (item.getItem() instanceof CitizenTalkingDevice) {
                item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0));
            }
        }
    }

    @SubscribeEvent
    private void onPlayerLeave(EntityLeaveLevelEvent event) {
        var entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player))
            return;
        var citizen = activeEntity.get(player.getUUID());
        if (citizen != null) {
            if (clients.containsKey(citizen.getUUID()))
                clients.get(citizen.getUUID()).close();

            clients.remove(citizen.getUUID());
        }

        activeEntity.remove(player.getUUID());
        playerLookingAt.remove(player.getUUID());
        lookDuration.remove(player.getUUID());
        previousEntityLookedAt.remove(player.getUUID());
        lastEntitySwitchTime.remove(player.getUUID());
        playerConversationPartners.remove(player.getUUID());
    }

    @SubscribeEvent
    private void onServerShutdown(ServerStoppingEvent event) {
        for (var client : clients.values()) {
            client.close();
        }

        clients.clear();
        activeEntity.clear();
        playerLookingAt.clear();
        lookDuration.clear();
        previousEntityLookedAt.clear();
        lastEntitySwitchTime.clear();
        playerConversationPartners.clear();
        addedEntities.clear();
    }

    int tick = 0;

    @SubscribeEvent
    private void onWorldTick(ServerTickEvent.Post event) {
        if (vcApi == null || Config.geminiApiKey.isEmpty())
            return;

        if (tick++ % 5 != 0) {
            // Every tick check for players who moved too far away
            checkPlayerDistances(event.getServer().getPlayerList().getPlayers());
            return;
        }

        // Check player distances to their conversation partners
        checkPlayerDistances(event.getServer().getPlayerList().getPlayers());

        // If talking device is enabled, don't use look-based activation
        if (Config.useTalkingDevice) {
            // When using the talking device, we still update positions but don't do look-based activation
            return;
        }

        var server = event.getServer();
        var players = new ArrayList<>(server.getPlayerList().getPlayers());

        for (ServerPlayer player : players) {
            if (player.isSpectator())
                continue;

            UUID playerId = player.getUUID();

            // Find the entity the player is looking at
            AbstractEntityCitizen currentTargetEntity = findEntityPlayerLookingAt(player);
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
                    // Start conversation with the citizen
                    startConversation(player, currentTargetEntity);
                }
            }
        }
    }

    /**
     * Check distances between players and their conversation partners
     * End conversations that exceed the maximum allowed distance
     *
     * @param players List of server players to check
     */
    private void checkPlayerDistances(List<ServerPlayer> players) {
        // Make a copy to avoid concurrent modification
        Set<UUID> playerIds = new HashSet<>(playerConversationPartners.keySet());

        for (UUID playerId : playerIds) {
            ServerPlayer player = null;

            // Find the player in the server's player list
            for (ServerPlayer serverPlayer : players) {
                if (serverPlayer.getUUID().equals(playerId)) {
                    player = serverPlayer;
                    break;
                }
            }

            if (player == null) {
                // Player not online anymore
                endConversation(playerId, false);
                continue;
            }

            UUID citizenId = playerConversationPartners.get(playerId);
            if (citizenId == null) continue;

            // Find the citizen entity
            AbstractEntityCitizen citizen = activeEntity.get(playerId);
            if (citizen == null || !citizen.isAlive()) {
                endConversation(playerId, false);
                continue;
            }

            // Check distance
            double distanceSquared = player.distanceToSqr(citizen);
            if (distanceSquared > (Config.maxConversationDistance * Config.maxConversationDistance)) {
                // Too far away, end conversation
                endConversation(playerId, true);
            }
        }
    }

    /**
     * Find the entity a player is currently looking at
     *
     * @param player The player to check
     * @return The entity the player is looking at, or null if none
     */
    private AbstractEntityCitizen findEntityPlayerLookingAt(ServerPlayer player) {
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

        playerLookingAt.remove(playerId);
        lookDuration.remove(playerId);
        activeEntity.remove(playerId);
    }

    public void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        registrar.playToClient(AiStatusPayload.TYPE, AiStatusPayload.STREAM_CODEC, new DirectionalPayloadHandler<>(
                (payload, ctx) -> {
                    ctx.enqueueWork(() -> {
                        aiStatus.put(payload.citizen(), payload.status());
                    });
                },
                (a, b) -> {
                }
        ));
    }
}
