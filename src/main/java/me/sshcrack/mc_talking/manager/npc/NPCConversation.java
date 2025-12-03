package me.sshcrack.mc_talking.manager.npc;

import com.minecolonies.api.colony.ICitizenData;
import me.sshcrack.mc_talking.McTalking;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a multi-NPC conversation session.
 * 
 * This class manages a conversation between multiple NPCs, where each NPC has its own
 * GeminiWsClient session. The conversation flow is managed server-side, with the server
 * deciding which NPC speaks next using a scheduler strategy.
 * 
 * <h2>Conversation Flow:</h2>
 * <ol>
 *   <li>NPCs are added to the conversation with their ICitizenData</li>
 *   <li>Each NPC gets its own NpcGeminiWsClient session</li>
 *   <li>When a message is received, the scheduler picks the next speaker</li>
 *   <li>The message is delivered to the selected NPC's session</li>
 *   <li>The NPC's response is broadcast to other NPCs as context</li>
 * </ol>
 */
public class NPCConversation {
    
    private final UUID conversationId;
    private final Map<UUID, NpcGeminiWsClient> npcClients;
    private final Map<UUID, ICitizenData> npcData;
    private final List<UUID> npcOrder; // Maintains order for round-robin
    private final List<ConversationMessage> messageHistory;
    private final NPCSpeakerScheduler scheduler;
    private UUID lastSpeaker;
    private int roundRobinIndex;
    private boolean active;
    
    /**
     * Creates a new multi-NPC conversation.
     * 
     * @param scheduler The scheduler strategy to use for selecting the next speaker
     */
    public NPCConversation(NPCSpeakerScheduler scheduler) {
        this.conversationId = UUID.randomUUID();
        this.npcClients = new ConcurrentHashMap<>();
        this.npcData = new ConcurrentHashMap<>();
        this.npcOrder = Collections.synchronizedList(new ArrayList<>());
        this.messageHistory = Collections.synchronizedList(new ArrayList<>());
        this.scheduler = scheduler != null ? scheduler : NPCSpeakerScheduler.ROUND_ROBIN;
        this.roundRobinIndex = 0;
        this.active = true;
    }
    
    /**
     * Creates a new multi-NPC conversation with default round-robin scheduling.
     */
    public NPCConversation() {
        this(NPCSpeakerScheduler.ROUND_ROBIN);
    }
    
    /**
     * Gets the unique identifier for this conversation.
     * 
     * @return The conversation UUID
     */
    public UUID getConversationId() {
        return conversationId;
    }
    
    /**
     * Adds an NPC to this conversation.
     * 
     * @param citizenData The citizen data for the NPC to add
     * @return true if the NPC was added successfully, false if already present
     */
    public boolean addNPC(ICitizenData citizenData) {
        UUID npcId = citizenData.getId() != 0 ? 
            UUID.nameUUIDFromBytes(String.valueOf(citizenData.getId()).getBytes()) :
            UUID.randomUUID();
        
        if (npcClients.containsKey(npcId)) {
            McTalking.LOGGER.warn("NPC {} is already in conversation {}", citizenData.getName(), conversationId);
            return false;
        }
        
        // Store citizen data
        npcData.put(npcId, citizenData);
        npcOrder.add(npcId);
        
        // Create NPC-specific Gemini client
        NpcGeminiWsClient client = new NpcGeminiWsClient(this, citizenData);
        npcClients.put(npcId, client);
        
        McTalking.LOGGER.info("Added NPC {} to conversation {}", citizenData.getName(), conversationId);
        return true;
    }
    
    /**
     * Removes an NPC from this conversation.
     * 
     * @param npcId The UUID of the NPC to remove
     * @return true if the NPC was removed, false if not found
     */
    public boolean removeNPC(UUID npcId) {
        NpcGeminiWsClient client = npcClients.remove(npcId);
        if (client != null) {
            client.close();
            npcData.remove(npcId);
            npcOrder.remove(npcId);
            
            // Adjust round-robin index if needed
            if (roundRobinIndex >= npcOrder.size()) {
                roundRobinIndex = 0;
            }
            return true;
        }
        return false;
    }
    
    /**
     * Sends a message to a specific NPC in the conversation.
     * 
     * @param targetNpcId The UUID of the target NPC
     * @param senderName The name of the sender (can be another NPC or external)
     * @param message The message content
     * @return true if the message was sent successfully
     */
    public boolean sendMessageToNPC(UUID targetNpcId, String senderName, String message) {
        NpcGeminiWsClient client = npcClients.get(targetNpcId);
        if (client == null) {
            McTalking.LOGGER.warn("Cannot send message to NPC {}: not in conversation", targetNpcId);
            return false;
        }
        
        // Format the message with sender context
        String formattedMessage = formatIncomingMessage(senderName, message);
        
        // Record in history
        recordMessage(senderName, targetNpcId, message);
        
        // Send to the NPC's Gemini session
        client.addSystemText(formattedMessage);
        return true;
    }
    
    /**
     * Broadcasts a message from one NPC to all other NPCs in the conversation.
     * This is used when an NPC responds and other NPCs need to hear it.
     * 
     * @param senderNpcId The UUID of the sending NPC
     * @param message The message content
     */
    public void broadcastToOthers(UUID senderNpcId, String message) {
        ICitizenData senderData = npcData.get(senderNpcId);
        String senderName = senderData != null ? senderData.getName() : "Unknown";
        
        // Record the message
        recordMessage(senderName, null, message);
        lastSpeaker = senderNpcId;
        
        // Send to all other NPCs
        for (Map.Entry<UUID, NpcGeminiWsClient> entry : npcClients.entrySet()) {
            if (!entry.getKey().equals(senderNpcId)) {
                String formattedMessage = formatIncomingMessage(senderName, message);
                entry.getValue().addSystemText(formattedMessage);
            }
        }
    }
    
    /**
     * Broadcasts a message to all NPCs in the conversation (e.g., from an external source).
     * 
     * @param senderName The name of the external sender
     * @param message The message content
     */
    public void broadcastToAll(String senderName, String message) {
        recordMessage(senderName, null, message);
        
        String formattedMessage = formatIncomingMessage(senderName, message);
        for (NpcGeminiWsClient client : npcClients.values()) {
            client.addSystemText(formattedMessage);
        }
    }
    
    /**
     * Determines the next NPC to speak based on the configured scheduler.
     * 
     * @return The UUID of the next speaker, or null if no NPCs available
     */
    public UUID getNextSpeaker() {
        if (npcOrder.isEmpty()) {
            return null;
        }
        
        return switch (scheduler) {
            case ROUND_ROBIN -> getNextRoundRobin();
            case RANDOM -> getNextRandom();
            case RANDOM_EXCLUDE_LAST -> getNextRandomExcludingLast();
        };
    }
    
    /**
     * Gets the next speaker using round-robin scheduling.
     */
    private UUID getNextRoundRobin() {
        if (npcOrder.isEmpty()) return null;
        
        UUID next = npcOrder.get(roundRobinIndex);
        roundRobinIndex = (roundRobinIndex + 1) % npcOrder.size();
        return next;
    }
    
    /**
     * Gets a random next speaker.
     */
    private UUID getNextRandom() {
        if (npcOrder.isEmpty()) return null;
        
        Random random = new Random();
        return npcOrder.get(random.nextInt(npcOrder.size()));
    }
    
    /**
     * Gets a random next speaker, excluding the last speaker if possible.
     */
    private UUID getNextRandomExcludingLast() {
        if (npcOrder.isEmpty()) return null;
        if (npcOrder.size() == 1) return npcOrder.get(0);
        
        Random random = new Random();
        List<UUID> candidates = new ArrayList<>(npcOrder);
        if (lastSpeaker != null) {
            candidates.remove(lastSpeaker);
        }
        
        return candidates.get(random.nextInt(candidates.size()));
    }
    
    /**
     * Formats an incoming message with sender context.
     */
    private String formatIncomingMessage(String senderName, String message) {
        return String.format("[%s says]: %s", senderName, message);
    }
    
    /**
     * Records a message in the conversation history.
     */
    private void recordMessage(String senderName, UUID targetNpcId, String message) {
        messageHistory.add(new ConversationMessage(
            System.currentTimeMillis(),
            senderName,
            targetNpcId,
            message
        ));
        
        // Keep history manageable
        while (messageHistory.size() > 100) {
            messageHistory.remove(0);
        }
    }
    
    /**
     * Gets a list of all NPCs in this conversation.
     * 
     * @return List of citizen data for all NPCs
     */
    public List<ICitizenData> getAllNPCs() {
        return new ArrayList<>(npcData.values());
    }
    
    /**
     * Gets the citizen data for a specific NPC.
     * 
     * @param npcId The UUID of the NPC
     * @return The citizen data, or null if not found
     */
    public ICitizenData getNPCData(UUID npcId) {
        return npcData.get(npcId);
    }
    
    /**
     * Gets the Gemini client for a specific NPC.
     * 
     * @param npcId The UUID of the NPC
     * @return The client, or null if not found
     */
    public NpcGeminiWsClient getNPCClient(UUID npcId) {
        return npcClients.get(npcId);
    }
    
    /**
     * Gets the number of NPCs in this conversation.
     * 
     * @return The NPC count
     */
    public int getNPCCount() {
        return npcClients.size();
    }
    
    /**
     * Checks if the conversation is still active.
     * 
     * @return true if active
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Gets recent message history for context.
     * 
     * @param count Maximum number of messages to return
     * @return List of recent messages
     */
    public List<ConversationMessage> getRecentHistory(int count) {
        int startIndex = Math.max(0, messageHistory.size() - count);
        return new ArrayList<>(messageHistory.subList(startIndex, messageHistory.size()));
    }
    
    /**
     * Ends the conversation and closes all NPC sessions.
     */
    public void end() {
        active = false;
        for (NpcGeminiWsClient client : npcClients.values()) {
            client.close();
        }
        npcClients.clear();
        npcData.clear();
        npcOrder.clear();
        McTalking.LOGGER.info("Ended conversation {}", conversationId);
    }
    
    /**
     * Record class representing a message in the conversation history.
     */
    public record ConversationMessage(
        long timestamp,
        String senderName,
        UUID targetNpcId, // null if broadcast
        String message
    ) {}
}
