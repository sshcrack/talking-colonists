package me.sshcrack.mc_talking.manager.npc;

import com.minecolonies.api.colony.ICitizenData;
import me.sshcrack.mc_talking.McTalking;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager class for orchestrating multiple NPC conversations.
 * 
 * This is the main entry point for creating and managing multi-NPC conversations.
 * It handles:
 * <ul>
 *   <li>Creating new conversations</li>
 *   <li>Adding/removing NPCs to/from conversations</li>
 *   <li>Routing messages between NPCs</li>
 *   <li>Managing conversation lifecycle</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Create a new conversation with round-robin scheduling
 * NPCConversation conversation = NPCConversationManager.createConversation(NPCSpeakerScheduler.ROUND_ROBIN);
 * 
 * // Add NPCs to the conversation
 * conversation.addNPC(citizen1Data);
 * conversation.addNPC(citizen2Data);
 * conversation.addNPC(citizen3Data);
 * 
 * // Start the conversation with an initial topic
 * NPCConversationManager.startConversation(conversation.getConversationId(), "Let's discuss the colony's food situation.");
 * 
 * // Send a message to a specific NPC
 * NPCConversationManager.sendToNPC(conversation.getConversationId(), targetNpcId, "Player", "What do you think about this?");
 * 
 * // Broadcast to all NPCs
 * NPCConversationManager.broadcastToAll(conversation.getConversationId(), "Town Crier", "Hear ye, hear ye!");
 * 
 * // End the conversation when done
 * NPCConversationManager.endConversation(conversation.getConversationId());
 * }</pre>
 */
public class NPCConversationManager {
    
    // Active conversations indexed by their UUID
    private static final Map<UUID, NPCConversation> activeConversations = new ConcurrentHashMap<>();
    
    // Maximum number of concurrent NPC conversations allowed
    private static final int MAX_CONVERSATIONS = 10;
    
    // Queue of conversation IDs for LRU eviction
    private static final Queue<UUID> conversationQueue = new LinkedList<>();
    
    /**
     * Creates a new multi-NPC conversation with the specified scheduler.
     * 
     * @param scheduler The scheduling strategy for selecting the next speaker
     * @return The created conversation
     */
    public static NPCConversation createConversation(NPCSpeakerScheduler scheduler) {
        enforceConversationLimit();
        
        NPCConversation conversation = new NPCConversation(scheduler);
        activeConversations.put(conversation.getConversationId(), conversation);
        conversationQueue.add(conversation.getConversationId());
        
        McTalking.LOGGER.info("Created new NPC conversation: {}", conversation.getConversationId());
        return conversation;
    }
    
    /**
     * Creates a new multi-NPC conversation with default round-robin scheduling.
     * 
     * @return The created conversation
     */
    public static NPCConversation createConversation() {
        return createConversation(NPCSpeakerScheduler.ROUND_ROBIN);
    }
    
    /**
     * Creates a conversation and adds the specified NPCs to it.
     * 
     * @param scheduler The scheduling strategy
     * @param npcs The NPCs to add to the conversation
     * @return The created conversation
     */
    public static NPCConversation createConversation(NPCSpeakerScheduler scheduler, ICitizenData... npcs) {
        NPCConversation conversation = createConversation(scheduler);
        
        for (ICitizenData npc : npcs) {
            conversation.addNPC(npc);
        }
        
        return conversation;
    }
    
    /**
     * Creates a conversation with default scheduling and adds the specified NPCs.
     * 
     * @param npcs The NPCs to add to the conversation
     * @return The created conversation
     */
    public static NPCConversation createConversation(ICitizenData... npcs) {
        return createConversation(NPCSpeakerScheduler.ROUND_ROBIN, npcs);
    }
    
    /**
     * Gets an existing conversation by its ID.
     * 
     * @param conversationId The UUID of the conversation
     * @return The conversation, or null if not found
     */
    public static NPCConversation getConversation(UUID conversationId) {
        return activeConversations.get(conversationId);
    }
    
    /**
     * Starts a conversation with an initial topic/message.
     * This broadcasts the topic to all NPCs and triggers the first speaker.
     * 
     * @param conversationId The UUID of the conversation
     * @param topic The initial topic or message to start the conversation
     * @return true if successful, false if conversation not found
     */
    public static boolean startConversation(UUID conversationId, String topic) {
        NPCConversation conversation = activeConversations.get(conversationId);
        if (conversation == null) {
            McTalking.LOGGER.warn("Cannot start conversation {}: not found", conversationId);
            return false;
        }
        
        if (conversation.getNPCCount() < 2) {
            McTalking.LOGGER.warn("Cannot start conversation {}: need at least 2 NPCs", conversationId);
            return false;
        }
        
        // Broadcast the topic to all NPCs
        conversation.broadcastToAll("Narrator", "A conversation begins: " + topic);
        
        // Get the first speaker and prompt them to respond
        UUID firstSpeaker = conversation.getNextSpeaker();
        if (firstSpeaker != null) {
            conversation.sendMessageToNPC(firstSpeaker, "Narrator", "You may speak first. React to the topic.");
        }
        
        McTalking.LOGGER.info("Started conversation {} with {} NPCs", 
            conversationId, conversation.getNPCCount());
        return true;
    }
    
    /**
     * Sends a message to a specific NPC in a conversation.
     * 
     * @param conversationId The UUID of the conversation
     * @param targetNpcId The UUID of the target NPC
     * @param senderName The name of the sender
     * @param message The message to send
     * @return true if successful, false otherwise
     */
    public static boolean sendToNPC(UUID conversationId, UUID targetNpcId, String senderName, String message) {
        NPCConversation conversation = activeConversations.get(conversationId);
        if (conversation == null) {
            McTalking.LOGGER.warn("Cannot send to NPC: conversation {} not found", conversationId);
            return false;
        }
        
        return conversation.sendMessageToNPC(targetNpcId, senderName, message);
    }
    
    /**
     * Broadcasts a message to all NPCs in a conversation.
     * 
     * @param conversationId The UUID of the conversation
     * @param senderName The name of the sender
     * @param message The message to broadcast
     * @return true if successful, false if conversation not found
     */
    public static boolean broadcastToAll(UUID conversationId, String senderName, String message) {
        NPCConversation conversation = activeConversations.get(conversationId);
        if (conversation == null) {
            McTalking.LOGGER.warn("Cannot broadcast: conversation {} not found", conversationId);
            return false;
        }
        
        conversation.broadcastToAll(senderName, message);
        return true;
    }
    
    /**
     * Advances the conversation by triggering the next speaker.
     * Uses the conversation's scheduler to determine who speaks next.
     * 
     * @param conversationId The UUID of the conversation
     * @param promptMessage A message to prompt the next speaker (optional)
     * @return The UUID of the next speaker, or null if failed
     */
    public static UUID advanceConversation(UUID conversationId, String promptMessage) {
        NPCConversation conversation = activeConversations.get(conversationId);
        if (conversation == null) {
            McTalking.LOGGER.warn("Cannot advance: conversation {} not found", conversationId);
            return null;
        }
        
        UUID nextSpeaker = conversation.getNextSpeaker();
        if (nextSpeaker == null) {
            McTalking.LOGGER.warn("No next speaker available in conversation {}", conversationId);
            return null;
        }
        
        // Prompt the next speaker
        String prompt = promptMessage != null && !promptMessage.isBlank() 
            ? promptMessage 
            : "It's your turn to respond.";
        
        conversation.sendMessageToNPC(nextSpeaker, "Narrator", prompt);
        return nextSpeaker;
    }
    
    /**
     * Ends a conversation and cleans up resources.
     * 
     * @param conversationId The UUID of the conversation to end
     * @return true if the conversation was ended, false if not found
     */
    public static boolean endConversation(UUID conversationId) {
        NPCConversation conversation = activeConversations.remove(conversationId);
        if (conversation == null) {
            return false;
        }
        
        conversationQueue.remove(conversationId);
        conversation.end();
        
        McTalking.LOGGER.info("Ended conversation {}", conversationId);
        return true;
    }
    
    /**
     * Ends all active conversations.
     * Call this during server shutdown.
     */
    public static void endAllConversations() {
        for (UUID id : new ArrayList<>(activeConversations.keySet())) {
            endConversation(id);
        }
        
        McTalking.LOGGER.info("Ended all NPC conversations");
    }
    
    /**
     * Gets the number of active conversations.
     * 
     * @return The count of active conversations
     */
    public static int getActiveConversationCount() {
        return activeConversations.size();
    }
    
    /**
     * Checks if a conversation exists and is active.
     * 
     * @param conversationId The UUID of the conversation
     * @return true if the conversation exists and is active
     */
    public static boolean isConversationActive(UUID conversationId) {
        NPCConversation conversation = activeConversations.get(conversationId);
        return conversation != null && conversation.isActive();
    }
    
    /**
     * Enforces the maximum conversation limit by ending the oldest conversation if needed.
     */
    private static void enforceConversationLimit() {
        while (activeConversations.size() >= MAX_CONVERSATIONS && !conversationQueue.isEmpty()) {
            UUID oldestId = conversationQueue.poll();
            if (oldestId != null) {
                endConversation(oldestId);
                McTalking.LOGGER.info("Ended oldest conversation {} due to limit", oldestId);
            }
        }
    }
}
