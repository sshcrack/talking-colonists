package me.sshcrack.mc_talking.manager.npc;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import me.sshcrack.mc_talking.manager.CitizenContextUtils;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/**
 * Generates compact system prompts for NPCs in multi-NPC conversations.
 * 
 * These prompts are designed to be concise while still providing all necessary
 * context for the NPC to maintain its identity and interact appropriately with
 * other NPCs in the conversation.
 * 
 * <h2>Prompt Structure:</h2>
 * <ol>
 *   <li>NPC Identity (name, gender, job, age)</li>
 *   <li>Key Personality Traits (from skills)</li>
 *   <li>Mood / Happiness</li>
 *   <li>Short Backstory Lines</li>
 *   <li>Memory Lines (events today)</li>
 *   <li>Other NPCs in the Conversation</li>
 *   <li>Conversation Rules</li>
 * </ol>
 */
public class NPCPromptGenerator {
    
    /**
     * Generates a compact system prompt for an NPC in a multi-NPC conversation.
     * 
     * @param npc The citizen data for this NPC
     * @param allNPCs All NPCs in the conversation (for context)
     * @return A compact system prompt string
     */
    public static String generateCompactNPCPrompt(ICitizenData npc, List<ICitizenData> allNPCs) {
        StringBuilder prompt = new StringBuilder();
        
        // Section 1: NPC Identity
        appendIdentity(prompt, npc);
        
        // Section 2: Key Personality Traits
        appendPersonalityTraits(prompt, npc);
        
        // Section 3: Mood / Happiness
        appendMood(prompt, npc);
        
        // Section 4: Short Backstory Lines
        appendBackstory(prompt, npc);
        
        // Section 5: Memory Lines (current events/status)
        appendMemory(prompt, npc);
        
        // Section 6: Other NPCs in Conversation
        appendOtherNPCs(prompt, npc, allNPCs);
        
        // Section 7: Conversation Rules
        appendConversationRules(prompt, npc);
        
        return prompt.toString();
    }
    
    /**
     * Appends NPC identity information to the prompt.
     * Includes name, gender, job, and age status.
     */
    private static void appendIdentity(StringBuilder prompt, ICitizenData npc) {
        prompt.append("# YOU ARE: ").append(npc.getName()).append("\n");
        
        // Gender and age
        String gender = npc.isFemale() ? "Female" : "Male";
        String age = npc.isChild() ? "Child" : "Adult";
        prompt.append("**").append(age).append(" ").append(gender).append("**");
        
        // Job
        var job = npc.getJob();
        if (job != null) {
            String jobName = Component.translatable(job.getJobRegistryEntry().getTranslationKey()).getString();
            prompt.append(" | Job: **").append(jobName).append("**");
        } else {
            prompt.append(" | **Unemployed**");
        }
        
        // Health status
        if (npc.getCitizenDiseaseHandler().isSick()) {
            prompt.append(" | Currently **sick**");
        }
        
        if (npc.getHomeBuilding() == null) {
            prompt.append(" | **Homeless**");
        }
        
        prompt.append("\n\n");
    }
    
    /**
     * Appends key personality traits derived from skills.
     * Only includes the top 2 skills for brevity.
     */
    private static void appendPersonalityTraits(StringBuilder prompt, ICitizenData npc) {
        ICitizenSkillHandler skillHandler = npc.getCitizenSkillHandler();
        if (skillHandler == null) return;
        
        Map<Skill, CitizenSkillHandler.SkillData> skills = skillHandler.getSkills();
        
        // Find top 2 skills
        Skill topSkill = null;
        int topLevel = -1;
        Skill secondSkill = null;
        int secondLevel = -1;
        
        for (Map.Entry<Skill, CitizenSkillHandler.SkillData> entry : skills.entrySet()) {
            int level = entry.getValue().getLevel();
            if (level > topLevel) {
                secondSkill = topSkill;
                secondLevel = topLevel;
                topSkill = entry.getKey();
                topLevel = level;
            } else if (level > secondLevel) {
                secondSkill = entry.getKey();
                secondLevel = level;
            }
        }
        
        if (topSkill != null && topLevel >= 2) {
            prompt.append("**Personality**: ");
            prompt.append(getTraitForSkill(topSkill));
            
            if (secondSkill != null && secondLevel >= 2) {
                prompt.append(", ").append(getTraitForSkill(secondSkill));
            }
            prompt.append("\n");
        }
    }
    
    /**
     * Maps a skill to a personality trait description.
     */
    private static String getTraitForSkill(Skill skill) {
        return switch (skill) {
            case Intelligence -> "Intellectual";
            case Strength -> "Tough";
            case Creativity -> "Creative";
            case Knowledge -> "Knowledgeable";
            case Dexterity -> "Nimble";
            case Adaptability -> "Adaptable";
            case Focus -> "Detail-oriented";
            case Mana -> "Mystical";
            case Athletics -> "Athletic";
            case Agility -> "Agile";
            case Stamina -> "Enduring";
        };
    }
    
    /**
     * Appends mood/happiness information.
     */
    private static void appendMood(StringBuilder prompt, ICitizenData npc) {
        var handler = npc.getCitizenHappinessHandler();
        double happiness = handler.getHappiness(npc.getColony(), npc);
        
        String moodDesc;
        if (happiness > 8.0) {
            moodDesc = "Very happy, cheerful";
        } else if (happiness > 6.0) {
            moodDesc = "Content, neutral";
        } else if (happiness > 4.0) {
            moodDesc = "Somewhat unhappy";
        } else if (happiness > 2.0) {
            moodDesc = "Unhappy, irritable";
        } else {
            moodDesc = "Miserable, hostile";
        }
        
        prompt.append("**Mood**: ").append(moodDesc);
        prompt.append(" (").append(String.format("%.1f", happiness)).append("/10)\n");
    }
    
    /**
     * Appends short backstory lines (relationships).
     */
    private static void appendBackstory(StringBuilder prompt, ICitizenData npc) {
        boolean hasBackstory = false;
        StringBuilder backstory = new StringBuilder();
        
        // Parents
        Tuple<String, String> parents = npc.getParents();
        if (parents != null) {
            if (parents.getA() != null && !parents.getA().isEmpty()) {
                if (!hasBackstory) {
                    backstory.append("**Background**: ");
                    hasBackstory = true;
                }
                backstory.append("Parent: ").append(parents.getA());
                if (parents.getB() != null && !parents.getB().isEmpty()) {
                    backstory.append(" & ").append(parents.getB());
                }
                backstory.append(". ");
            }
        }
        
        // Partner
        if (npc.getPartner() != null) {
            if (!hasBackstory) {
                backstory.append("**Background**: ");
                hasBackstory = true;
            }
            backstory.append("Has a partner. ");
        }
        
        // Children
        List<Integer> children = npc.getChildren();
        if (children != null && !children.isEmpty()) {
            if (!hasBackstory) {
                backstory.append("**Background**: ");
                hasBackstory = true;
            }
            backstory.append("Has ").append(children.size()).append(" child(ren). ");
        }
        
        if (hasBackstory) {
            prompt.append(backstory).append("\n");
        }
    }
    
    /**
     * Appends memory/current events (what's happening today).
     */
    private static void appendMemory(StringBuilder prompt, ICitizenData npc) {
        StringBuilder memory = new StringBuilder();
        boolean hasMemory = false;
        
        // Current activity
        VisibleCitizenStatus status = npc.getStatus();
        if (status != null) {
            memory.append("**Now**: ").append(CitizenContextUtils.formatStatus(status, npc)).append(". ");
            hasMemory = true;
        }
        
        // Health/hunger concerns
        double saturation = npc.getSaturation();
        if (saturation <= 3) {
            if (!hasMemory) memory.append("**Now**: ");
            memory.append("Hungry. ");
            hasMemory = true;
        }
        
        // Sickness
        if (npc.getCitizenDiseaseHandler().isSick()) {
            if (!hasMemory) memory.append("**Now**: ");
            memory.append("Feeling ill. ");
            hasMemory = true;
        }
        
        if (hasMemory) {
            prompt.append(memory).append("\n");
        }
    }
    
    /**
     * Appends information about other NPCs in the conversation.
     */
    private static void appendOtherNPCs(StringBuilder prompt, ICitizenData thisNpc, List<ICitizenData> allNPCs) {
        if (allNPCs.size() <= 1) return;
        
        prompt.append("\n**Others in conversation**:\n");
        
        for (ICitizenData other : allNPCs) {
            // Skip self
            if (other.getId() == thisNpc.getId()) continue;
            
            String gender = other.isFemale() ? "F" : "M";
            String age = other.isChild() ? "child" : "";
            
            prompt.append("- **").append(other.getName()).append("**");
            prompt.append(" (").append(gender);
            if (!age.isEmpty()) prompt.append(", ").append(age);
            prompt.append(")");
            
            // Their job
            var otherJob = other.getJob();
            if (otherJob != null) {
                String jobName = Component.translatable(otherJob.getJobRegistryEntry().getTranslationKey()).getString();
                prompt.append(" - ").append(jobName);
            }
            
            // Check if they're related
            if (isRelated(thisNpc, other)) {
                prompt.append(" [family]");
            }
            
            prompt.append("\n");
        }
    }
    
    /**
     * Checks if two NPCs are related (parent, child, sibling, or partner).
     */
    private static boolean isRelated(ICitizenData npc1, ICitizenData npc2) {
        int id2 = npc2.getId();
        
        // Check if npc2 is a child of npc1
        if (npc1.getChildren() != null && npc1.getChildren().contains(id2)) {
            return true;
        }
        
        // Check if npc2 is a sibling of npc1
        if (npc1.getSiblings() != null && npc1.getSiblings().contains(id2)) {
            return true;
        }
        
        // Check if npc2 is the partner of npc1
        if (npc1.getPartner() != null && npc1.getPartner() == id2) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Appends conversation rules to ensure proper multi-NPC behavior.
     */
    private static void appendConversationRules(StringBuilder prompt, ICitizenData npc) {
        prompt.append("\n## RULES\n");
        prompt.append("- Speak ONLY as ").append(npc.getName()).append(" (first person)\n");
        prompt.append("- Respond to what others say; be reactive\n");
        prompt.append("- Keep messages SHORT (1-2 sentences max)\n");
        prompt.append("- NEVER simulate or speak for other characters\n");
        prompt.append("- Stay in character based on your mood and personality\n");
        prompt.append("- Use plain text, no markdown\n");
        prompt.append("- Respond in ").append(CitizenContextUtils.getLanguageNameFromCode(CONFIG.language.get())).append("\n");
    }
}
