package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.McTalking;

import java.util.ArrayList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/**
 * Centralized manager for tracking all active Gemini WebSocket sessions.
 * 
 * This ensures that both player-NPC conversations and NPC-NPC conversations
 * respect the configured maximum concurrent agents limit.
 */
public class SessionManager {
    
    // All active session IDs (entity UUIDs or conversation-specific IDs)
    private static final Set<UUID> activeSessions = ConcurrentHashMap.newKeySet();
    
    // Queue for LRU eviction when limit is exceeded
    private static final Queue<UUID> sessionQueue = new ConcurrentLinkedQueue<>();
    
    // Callbacks for session eviction
    private static final Map<UUID, Runnable> evictionCallbacks = new ConcurrentHashMap<>();
    
    /**
     * Attempts to acquire a session slot.
     * 
     * @param sessionId The unique ID for this session
     * @param evictionCallback Callback to run if this session is evicted
     * @return true if the session was acquired, false if limit reached and eviction failed
     */
    public static boolean acquireSession(UUID sessionId, Runnable evictionCallback) {
        // Already have this session
        if (activeSessions.contains(sessionId)) {
            return true;
        }
        
        // Enforce the limit
        int maxSessions = CONFIG.maxConcurrentAgents.get();
        
        while (activeSessions.size() >= maxSessions) {
            UUID oldestSession = sessionQueue.poll();
            if (oldestSession == null) {
                McTalking.LOGGER.warn("Cannot acquire session {}: limit reached and no sessions to evict", sessionId);
                return false;
            }
            
            evictSession(oldestSession);
        }
        
        // Add the new session
        activeSessions.add(sessionId);
        sessionQueue.add(sessionId);
        if (evictionCallback != null) {
            evictionCallbacks.put(sessionId, evictionCallback);
        }
        
        McTalking.LOGGER.debug("Acquired session {}, total active: {}", sessionId, activeSessions.size());
        return true;
    }
    
    /**
     * Releases a session slot.
     * 
     * @param sessionId The session ID to release
     */
    public static void releaseSession(UUID sessionId) {
        if (activeSessions.remove(sessionId)) {
            sessionQueue.remove(sessionId);
            evictionCallbacks.remove(sessionId);
            McTalking.LOGGER.debug("Released session {}, total active: {}", sessionId, activeSessions.size());
        }
    }
    
    /**
     * Evicts a session, calling its eviction callback.
     */
    private static void evictSession(UUID sessionId) {
        if (activeSessions.remove(sessionId)) {
            Runnable callback = evictionCallbacks.remove(sessionId);
            if (callback != null) {
                try {
                    callback.run();
                } catch (Exception e) {
                    McTalking.LOGGER.error("Error during session eviction callback for {}", sessionId, e);
                }
            }
            McTalking.LOGGER.info("Evicted session {} due to limit", sessionId);
        }
    }
    
    /**
     * Checks if a session is currently active.
     * 
     * @param sessionId The session ID to check
     * @return true if the session is active
     */
    public static boolean isSessionActive(UUID sessionId) {
        return activeSessions.contains(sessionId);
    }
    
    /**
     * Gets the current number of active sessions.
     * 
     * @return The count of active sessions
     */
    public static int getActiveSessionCount() {
        return activeSessions.size();
    }
    
    /**
     * Gets the maximum allowed sessions from config.
     * 
     * @return The maximum concurrent agents allowed
     */
    public static int getMaxSessions() {
        return CONFIG.maxConcurrentAgents.get();
    }
    
    /**
     * Clears all sessions. Call during server shutdown.
     */
    public static void clearAll() {
        for (UUID sessionId : new ArrayList<>(activeSessions)) {
            evictSession(sessionId);
        }
        activeSessions.clear();
        sessionQueue.clear();
        evictionCallbacks.clear();
        McTalking.LOGGER.info("Cleared all sessions");
    }
}
