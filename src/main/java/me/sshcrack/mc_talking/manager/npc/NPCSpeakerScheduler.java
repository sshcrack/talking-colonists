package me.sshcrack.mc_talking.manager.npc;

/**
 * Enum representing different strategies for selecting the next NPC speaker
 * in a multi-NPC conversation.
 * 
 * The server uses this scheduler to decide which NPC should speak next,
 * rather than relying on any "next_speaker" tool calls from the AI.
 */
public enum NPCSpeakerScheduler {
    
    /**
     * NPCs take turns in a fixed order.
     * Each NPC speaks once before the cycle repeats.
     */
    ROUND_ROBIN,
    
    /**
     * A random NPC is selected to speak next.
     * The same NPC may speak multiple times in a row.
     */
    RANDOM,
    
    /**
     * A random NPC is selected, but the last speaker is excluded.
     * This prevents the same NPC from speaking twice in a row.
     */
    RANDOM_EXCLUDE_LAST
}
