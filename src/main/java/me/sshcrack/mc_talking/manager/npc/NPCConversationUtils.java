package me.sshcrack.mc_talking.manager.npc;

import com.minecolonies.api.colony.ICitizenData;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for NPC conversation operations.
 */
public final class NPCConversationUtils {
    
    private NPCConversationUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Generates a consistent UUID for an NPC based on their citizen data.
     * This ensures the same NPC always gets the same UUID within conversations.
     * 
     * @param citizenData The citizen data for the NPC
     * @return A UUID for the NPC
     */
    public static UUID generateNpcId(ICitizenData citizenData) {
        if (citizenData.getId() != 0) {
            return UUID.nameUUIDFromBytes(
                String.valueOf(citizenData.getId()).getBytes(StandardCharsets.UTF_8)
            );
        }
        return UUID.randomUUID();
    }
    
    /**
     * Gets a random element from a list using ThreadLocalRandom.
     * 
     * @param <T> The type of elements in the list
     * @param list The list to select from
     * @return A random element, or null if list is empty
     */
    public static <T> T getRandomElement(java.util.List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }
}
