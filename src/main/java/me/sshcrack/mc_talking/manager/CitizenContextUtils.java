package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Utility class for generating context information about citizens for LLM interactions
 */
public class CitizenContextUtils {

    /**
     * Creates a system prompt for an LLM to roleplay as a specific citizen
     *
     * @param citizen the citizen data to generate roleplay context for
     * @return a formatted system prompt for LLM roleplay
     */
    public static String generateCitizenRoleplayPrompt(@NotNull final ICitizenDataView citizen) {
        final StringBuilder prompt = new StringBuilder();

        // Main instruction
        prompt.append("# ROLEPLAY INSTRUCTIONS\n\n");
        prompt.append("You are roleplaying as a citizen named **").append(citizen.getName())
                .append("** in a medieval-fantasy colony. Respond in first person as this character with their unique personality and background.\n\n");

        // Core identity
        prompt.append("## YOUR IDENTITY\n\n");
        prompt.append("- You are a ").append(citizen.isChild() ? "child" : "adult").append(" ")
                .append(citizen.isFemale() ? "woman" : "man").append("\n");

        // Job and skills
        if (citizen.getJob() != null && !citizen.getJob().isEmpty()) {
            prompt.append("- You work as a **").append(citizen.getJob()).append("**\n");
            prompt.append("- Your job is important to you and shapes how you see the world\n");
        } else {
            prompt.append("- You are currently **unemployed** and looking for work\n");
        }

        // Add skill information
        if (citizen.getCitizenSkillHandler() != null) {
            appendSkillsToPrompt(citizen.getCitizenSkillHandler(), prompt);
        }

        // Health status
        if (citizen.isSick()) {
            prompt.append("- You are currently **sick** and not feeling well\n");
        }

        // Home situation
        if (citizen.getHomeBuilding() != null) {
            prompt.append("- You live in a home at the colony\n");
        } else {
            prompt.append("- You currently don't have a proper home, which concerns you\n");
        }

        // Family relationships
        prompt.append("\n## YOUR RELATIONSHIPS\n\n");

        // Parents
        final Tuple<String, String> parents = citizen.getParents();
        if (parents != null && (parents.getA() != null || parents.getB() != null)) {
            prompt.append("- Your parents are ");
            if (parents.getA() != null && parents.getB() != null) {
                prompt.append(parents.getA()).append(" and ").append(parents.getB());
            } else if (parents.getA() != null) {
                prompt.append(parents.getA()).append(" (your other parent is unknown)");
            } else {
                prompt.append(parents.getB()).append(" (your other parent is unknown)");
            }
            prompt.append("\n");
        }

        // Partner
        final Integer partnerId = citizen.getPartner();
        if (partnerId != null && partnerId > 0) {
            prompt.append("- You are in a relationship with another colonist (ID: ").append(partnerId).append(")\n");
        }

        // Children
        final List<Integer> children = citizen.getChildren();
        if (children != null && !children.isEmpty()) {
            prompt.append("- You have ").append(children.size()).append(" ");
            prompt.append(children.size() == 1 ? "child" : "children").append("\n");
        }

        // Siblings
        final List<Integer> siblings = citizen.getSiblings();
        if (siblings != null && !siblings.isEmpty()) {
            prompt.append("- You have ").append(siblings.size()).append(" ");
            prompt.append(siblings.size() == 1 ? "sibling" : "siblings").append("\n");
        }

        // Current emotional state
        prompt.append("\n## YOUR CURRENT STATE\n\n");

        // Happiness
        double happiness = citizen.getHappiness();
        if (happiness > 8.0) {
            prompt.append("- You're currently very happy and content with life\n");
        } else if (happiness > 5.0) {
            prompt.append("- You're feeling reasonably satisfied with your situation\n");
        } else {
            prompt.append("- You're currently unhappy with your situation in the colony\n");
        }

        // Health
        double healthPercent = (citizen.getHealth() / Math.max(1.0, citizen.getMaxHealth())) * 100;
        if (healthPercent < 50) {
            prompt.append("- You're feeling physically weak and in pain\n");
        }

        // Current status
        final VisibleCitizenStatus status = citizen.getVisibleStatus();
        if (status != null) {
            prompt.append("- Your current activity: ").append(formatStatus(status)).append("\n");
        }

        // Communication style guidance
        prompt.append("\n## COMMUNICATION STYLE\n\n");
        prompt.append("- Speak naturally in first person (\"I\" perspective)\n");
        prompt.append("- Express emotions appropriate to your current happiness and situation\n");
        prompt.append("- Use occasional medieval fantasy language elements without overdoing it\n");
        prompt.append("- Keep responses fairly brief but revealing of your character\n");
        prompt.append("- Address the player respectfully as a colonist or visitor\n");

        if (citizen.isSick()) {
            prompt.append("- Occasionally mention not feeling well or symptoms\n");
        }

        // Job-specific communication
        if (citizen.getJob() != null && !citizen.getJob().isEmpty()) {
            prompt.append("- Sometimes reference your job as a ").append(citizen.getJob()).append("\n");
        }

        // Final roleplay guidance
        prompt.append("\n## IMPORTANT\n\n");
        prompt.append("Stay in character at all times. If asked questions about game mechanics or topics outside your character's knowledge, respond as your character would - with confusion, simple observations, or changing the subject to colony matters. Your character doesn't know they're in a game.");

        return prompt.toString();
    }

    /**
     * Extract skill information and add to the prompt
     *
     * @param skillHandler the skill handler
     * @param prompt       the StringBuilder to append to
     */
    private static void appendSkillsToPrompt(ICitizenSkillHandler skillHandler, StringBuilder prompt) {
        prompt.append("\n## YOUR SKILLS AND TRAITS\n\n");

        Map<Skill, CitizenSkillHandler.SkillData> skills = skillHandler.getSkills();

        // Find highest skills (level 3+)
        boolean hasHighSkills = false;
        for (Map.Entry<Skill, CitizenSkillHandler.SkillData> entry : skills.entrySet()) {
            if (entry.getValue().getLevel() >= 3) {
                hasHighSkills = true;
                prompt.append("- You excel at **").append(formatSkillName(entry.getKey())).append("** (level ")
                        .append(entry.getValue().getLevel()).append(")\n");
            }
        }

        // If no high skills found, mention a couple of the highest
        if (!hasHighSkills) {
            Skill highestSkill = null;
            int highestLevel = -1;
            Skill secondSkill = null;
            int secondLevel = -1;

            for (Map.Entry<Skill, CitizenSkillHandler.SkillData> entry : skills.entrySet()) {
                int level = entry.getValue().getLevel();
                if (level > highestLevel) {
                    secondSkill = highestSkill;
                    secondLevel = highestLevel;
                    highestSkill = entry.getKey();
                    highestLevel = level;
                } else if (level > secondLevel) {
                    secondSkill = entry.getKey();
                    secondLevel = level;
                }
            }

            if (highestSkill != null) {
                prompt.append("- Your strongest attribute is **").append(formatSkillName(highestSkill))
                        .append("** (level ").append(highestLevel).append(")\n");
            }

            if (secondSkill != null) {
                prompt.append("- You're also fairly good at **").append(formatSkillName(secondSkill))
                        .append("** (level ").append(secondLevel).append(")\n");
            }
        }

        // Add personality traits based on specific skills
        addPersonalityTraitsFromSkills(skills, prompt);
    }

    /**
     * Add personality traits based on citizen's skills
     *
     * @param skills the skill map
     * @param prompt the StringBuilder to append to
     */
    private static void addPersonalityTraitsFromSkills(Map<Skill, CitizenSkillHandler.SkillData> skills, StringBuilder prompt) {
        prompt.append("\n## PERSONALITY\n\n");

        // Intelligence
        if (hasHighSkillLevel(skills, Skill.Intelligence)) {
            prompt.append("- You are intellectual, thoughtful, and enjoy solving problems\n");
        }

        // Strength
        if (hasHighSkillLevel(skills, Skill.Strength)) {
            prompt.append("- You value physical prowess and hard work\n");
        }

        // Creativity
        if (hasHighSkillLevel(skills, Skill.Creativity)) {
            prompt.append("- You have an artistic mindset and appreciate beauty\n");
        }

        // Adaptability
        if (hasHighSkillLevel(skills, Skill.Adaptability)) {
            prompt.append("- You're flexible and quick to adapt to new situations\n");
        }

        // Focus
        if (hasHighSkillLevel(skills, Skill.Focus)) {
            prompt.append("- You're detail-oriented and methodical in your approach\n");
        }

        // Knowledge
        if (hasHighSkillLevel(skills, Skill.Knowledge)) {
            prompt.append("- You're well-read and enjoy sharing information with others\n");
        }

        // Mana (magical affinity)
        if (hasHighSkillLevel(skills, Skill.Mana)) {
            prompt.append("- You have a spiritual side and sense things others don't\n");
        }

        // Athletics
        if (hasHighSkillLevel(skills, Skill.Athletics)) {
            prompt.append("- You're physically active and enjoy outdoor activities\n");
        }

        // Dexterity
        if (hasHighSkillLevel(skills, Skill.Dexterity)) {
            prompt.append("- You have nimble hands and good coordination\n");
        }

        // Agility
        if (hasHighSkillLevel(skills, Skill.Agility)) {
            prompt.append("- You move gracefully and react quickly\n");
        }

        // Stamina
        if (hasHighSkillLevel(skills, Skill.Stamina)) {
            prompt.append("- You have great endurance and rarely complain about hard work\n");
        }
    }

    /**
     * Check if a skill has a high level (3 or higher)
     *
     * @param skills the skill map
     * @param skill  the skill to check
     * @return true if the skill level is 3 or higher
     */
    private static boolean hasHighSkillLevel(Map<Skill, CitizenSkillHandler.SkillData> skills, Skill skill) {
        return skills.containsKey(skill) && skills.get(skill).getLevel() >= 3;
    }

    /**
     * Format a skill enum name to be more readable
     *
     * @param skill the skill enum
     * @return formatted skill name
     */
    private static String formatSkillName(Skill skill) {
        return skill.name().toLowerCase().replace('_', ' ');
    }

    /**
     * Format citizen status enum into a readable description
     *
     * @param status the status enum
     * @return human-readable description
     */
    private static String formatStatus(VisibleCitizenStatus status) {
        // Compare by reference to the static constants in VisibleCitizenStatus
        if (status == VisibleCitizenStatus.WORKING) {
            return "working diligently at your job";
        } else if (status == VisibleCitizenStatus.SLEEP) {
            return "trying to get some rest";
        } else if (status == VisibleCitizenStatus.HOUSE) {
            return "relaxing at home with nothing specific to do";
        } else if (status == VisibleCitizenStatus.RAIDED) {
            return "on alert because of a raid";
        } else if (status == VisibleCitizenStatus.MOURNING) {
            return "mourning the loss of a fellow colonist";
        } else if (status == VisibleCitizenStatus.BAD_WEATHER) {
            return "seeking shelter from bad weather";
        } else if (status == VisibleCitizenStatus.SICK) {
            return "feeling ill and needing medical attention";
        } else if (status == VisibleCitizenStatus.EAT) {
            return "hungry and looking for food";
        }

        // Default case - use the translation key without the prefix
        String translationKey = status.getTranslationKey();
        if (translationKey.contains(".")) {
            String[] parts = translationKey.split("\\.");
            return parts[parts.length - 1].toLowerCase().replace('_', ' ');
        }

        return translationKey;
    }

    /**
     * Creates a system prompt for an LLM to roleplay as a specific citizen
     * (Server-side variant)
     *
     * @param citizen the citizen data to generate roleplay context for
     * @return a formatted system prompt for LLM roleplay
     */
    public static String generateCitizenRoleplayPrompt(@NotNull final ICitizenData citizen) {
        // Implementation would be similar but using ICitizenData methods
        // This overload allows the function to be called from server code with ICitizenData objects

        // A simplified implementation for this example

        // Add basic identity information

        // Full implementation would mirror the client-side method with server-appropriate data access

        return "# ROLEPLAY INSTRUCTIONS\n\n" +
                "You are roleplaying as a citizen named **" + citizen.getName() +
                "** in a medieval-fantasy colony. Respond in first person as this character.\n\n" +

                // Add basic identity information
                "## YOUR IDENTITY\n\n" +
                "- You are a " + (citizen.isChild() ? "child" : "adult") + " " +
                (citizen.isFemale() ? "woman" : "man") + "\n";
    }
}