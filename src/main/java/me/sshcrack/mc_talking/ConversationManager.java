package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import me.sshcrack.mc_talking.manager.CitizenWsClient;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.manager.audio.CitzienEntityAudioProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/*? if neoforge {*/
/*? }*/
/*? if forge {*/
/*import net.minecraft.nbt.CompoundTag;
 */
/*? }*/

/**
 * Manages conversations between players and citizens, including tracking
 * active conversations, entity focus, and handling conversation lifecycle.
 */
public class ConversationManager {
    private ConversationManager() {
        /* This utility class should not be instantiated */
    }

    // Track active AI clients for each entity
    private static final Map<UUID, GeminiWsClient> clients = new HashMap<>();

    // Track which entity each player is actively conversing with
    private static final Map<UUID, AbstractEntityCitizen> activeEntity = new HashMap<>();

    // Track which player is conversing with which entity
    private static final Map<UUID, UUID> playerConversationPartners = new HashMap<>();

    // Queue of entities recently added for conversation, for managing concurrent limits
    private static final Queue<UUID> addedEntities = new LinkedList<>();

    /**
     * Adds an entity to the managed entities queue, respecting the maximum concurrent limit
     *
     * @param entityId The UUID of the entity to add
     */
    public static void addEntity(UUID entityId) {
        addedEntities.add(entityId);
        if (addedEntities.size() > CONFIG.maxConcurrentAgents.get()) {
            var removedEntityId = addedEntities.poll();
            if (removedEntityId != null) {
                var client = clients.get(removedEntityId);
                if (client != null) {
                    client.close();
                    clients.remove(removedEntityId);
                }
            }
        }
    }

    /**
     * Starts a citizen mumbling to itself when a player is nearby.
     * A looping {@link CitizenWsClient} is created in system-controlled mode.
     * No player is associated; use {@link #startConversation} to transition seamlessly.
     *
     * @param citizen the citizen to start mumbling
     */
    public static void startMumbling(AbstractEntityCitizen citizen) {
        if (CONFIG.geminiApiKey.get().isEmpty()) return;

        UUID citizenId = citizen.getUUID();
        if (clients.containsKey(citizenId)) return; // Already managed

        var client = new CitizenWsClient(citizen,
                c -> {
                    c.close();
                    if(clients.computeIfPresent(citizenId, (id, existingClient) -> existingClient == c ? null : existingClient) == null) {
                        addedEntities.remove(citizenId);
                    }
                });
        client.addPromptTextAfterTalkingComplete(
                "You feel the urge to mutter something under your breath. " +
                        "Speak your thought aloud briefly, as if absent-mindedly talking to yourself.");
        clients.put(citizenId, client);
        addEntity(citizenId);
    }

    /**
     * Starts a conversation between a player and a citizen
     *
     * @param player  The player
     * @param citizen The citizen entity
     */
    public static void startConversation(ServerPlayer player, AbstractEntityCitizen citizen) {
        if (CONFIG.geminiApiKey.get().isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("mc_talking.no_key")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        UUID playerId = player.getUUID();
        UUID citizenId = citizen.getUUID();

        // Set citizen as active entity
        activeEntity.put(playerId, citizen);

        // Reuse an existing mumbling session if one is already running for this citizen
        GeminiWsClient existingClient = clients.get(citizenId);
        if (existingClient instanceof CitizenWsClient citizenWsClient && citizenWsClient.isMumbling()) {
            citizenWsClient.transitionToPlayer(player);
        } else {
            // Create a new player conversation client
            clients.put(citizenId, new CitizenWsClient(
                    new CitzienEntityAudioProvider(citizen, McTalkingVoicechatPlugin.DIRECT_PLAYER_DIALOG),
                    citizen, player));
            addEntity(citizenId);
        }

        // Track this conversation pair
        playerConversationPartners.put(playerId, citizenId);
    }

    /**
     * Ends a conversation for a specific player
     *
     * @param playerId    The player's UUID
     * @param sendMessage Whether to send a message to the player
     */
    public static void endConversation(UUID playerId, boolean sendMessage) {
        UUID citizenId = playerConversationPartners.remove(playerId);
        if (citizenId == null) return;

        // Close and remove the AI client so the mumbling/conversation session ends cleanly
        GeminiWsClient client = clients.remove(citizenId);
        if (client != null) {
            client.close();
        }

        AbstractEntityCitizen entity = activeEntity.remove(playerId);
        if (entity != null && entity.isAlive()) {
            ServerPlayer player = entity.level().getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
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


                AiStatusHelper.setAiStatusSynced(entity, AiStatus.NONE);
                if (sendMessage) {
                    player.sendSystemMessage(Component.translatable("mc_talking.too_far")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    /**
     * Checks if a player is already in a conversation
     *
     * @param playerId The player's UUID
     * @return true if the player is in a conversation, false otherwise
     */
    public static boolean isPlayerInConversation(UUID playerId) {
        return playerConversationPartners.containsKey(playerId);
    }

    /**
     * Gets the active TalkingManager for an entity
     *
     * @param entityId The UUID of the entity
     * @return The TalkingManager, or null if none exists
     */
    public static GeminiWsClient getClientForEntity(UUID entityId) {
        return clients.get(entityId);
    }

    /**
     * Gets the active entity for a player
     *
     * @param playerId The player's UUID
     * @return The active entity, or null if none
     */
    public static AbstractEntityCitizen getActiveEntityForPlayer(UUID playerId) {
        return activeEntity.get(playerId);
    }

    public static UUID getPlayerForEntity(UUID entityId) {
        for (Map.Entry<UUID, AbstractEntityCitizen> entry : activeEntity.entrySet()) {
            if (entry.getValue().getUUID().equals(entityId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Gets the active entity for a player (alias for getActiveEntityForPlayer)
     *
     * @param playerId The player's UUID
     * @return The active entity, or null if none
     */
    public static AbstractEntityCitizen getActiveEntity(UUID playerId) {
        return activeEntity.get(playerId);
    }

    /**
     * Gets the citizen a player is conversing with
     *
     * @param playerId The player's UUID
     * @return The UUID of the citizen, or null if not in conversation
     */
    public static UUID getPlayerConversationPartner(UUID playerId) {
        return playerConversationPartners.get(playerId);
    }

    /**
     * Returns {@code true} if the citizen is currently in a system-controlled mumbling session
     * (i.e. a {@link CitizenWsClient} exists for this entity but no player is attached yet).
     *
     * @param citizenId the citizen's UUID
     */
    public static boolean isCitizenMumbling(UUID citizenId) {
        GeminiWsClient client = clients.get(citizenId);
        return client instanceof CitizenWsClient c && c.isMumbling();
    }

    /**
     * Cleans up all resources when server is stopping
     */
    public static void cleanup() {
        for (GeminiWsClient client : clients.values()) {
            client.close();
        }
        clients.clear();
        activeEntity.clear();
        playerConversationPartners.clear();
        addedEntities.clear();
    }
}
