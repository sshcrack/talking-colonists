package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import me.sshcrack.mc_talking.Config;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for generating context information about citizens for LLM interactions
 */
public class CitizenContextUtils {

    /**
     * Converts a locale code (like "de-DE") to a language name (like "German")
     * @param localeCode The locale code (e.g. "de-DE", "en-US", etc.)
     * @return The language name in English
     */
    public static String getLanguageNameFromCode(String localeCode) {
        try {
            // Split the code to get the language part (first part before dash)
            String[] parts = localeCode.split("-");
            String languageCode = parts[0];

            // Create a locale using Locale.forLanguageTag() instead of the deprecated constructor
            Locale locale = Locale.forLanguageTag(languageCode);

            // Get the display language in English
            return locale.getDisplayLanguage(Locale.ENGLISH);
        } catch (Exception e) {
            return localeCode; // Return the original code if conversion fails
        }
    }

    /**
     * Creates a system prompt for an LLM to roleplay as a specific citizen
     *
     * @param citizen the citizen data to generate roleplay context for
     * @return a formatted system prompt for LLM roleplay
     */
    public static String generateCitizenRoleplayPrompt(@NotNull final ICitizenDataView citizen, ServerPlayer speakingTo) {
        final StringBuilder prompt = new StringBuilder();

        // Main instruction - more concise
        prompt.append("# ROLEPLAY AS ").append(citizen.getName()).append("\n\n");
        prompt.append("You: ").append(citizen.isChild() ? "Child" : "Adult").append(" ")
                .append(citizen.isFemale() ? "woman" : "man");

        // Job
        if (citizen.getJob() != null && !citizen.getJob().isEmpty()) {
            prompt.append(", **").append(citizen.getJob()).append("**");
        } else {
            prompt.append(", **unemployed**");
        }

        // Key status in same line
        if (citizen.isSick()) {
            prompt.append(", sick");
        }

        if (citizen.getHomeBuilding() == null) {
            prompt.append(", homeless");
        }

        prompt.append(".\n\n");

        // Skills - only include if available
        if (citizen.getCitizenSkillHandler() != null) {
            appendCondensedSkills(citizen.getCitizenSkillHandler(), prompt);
        }

        // Relationships - only include if they exist
        boolean hasRelationships = false;

        // Parents
        final Tuple<String, String> parents = citizen.getParents();
        if (parents != null && (parents.getA() != null || parents.getB() != null)) {
            if (!hasRelationships) {
                prompt.append("\n## RELATIONSHIPS\n");
                hasRelationships = true;
            }
            prompt.append("- Parents: ");
            if (parents.getA() != null && parents.getB() != null) {
                prompt.append(parents.getA()).append(", ").append(parents.getB());
            } else if (parents.getA() != null) {
                prompt.append(parents.getA());
            } else {
                prompt.append(parents.getB());
            }
            prompt.append("\n");
        }

        // Partner
        final Integer partnerId = citizen.getPartner();
        if (partnerId != null && partnerId > 0) {
            if (!hasRelationships) {
                prompt.append("\n## RELATIONSHIPS\n");
                hasRelationships = true;
            }
            prompt.append("- In a relationship\n");
        }

        // Children and Siblings (simplified)
        final List<Integer> children = citizen.getChildren();
        if (children != null && !children.isEmpty()) {
            if (!hasRelationships) {
                prompt.append("\n## RELATIONSHIPS\n");
                hasRelationships = true;
            }
            prompt.append("- Has ").append(children.size()).append(" ").append(children.size() == 1 ? "child" : "children").append("\n");
        }

        final List<Integer> siblings = citizen.getSiblings();
        if (siblings != null && !siblings.isEmpty()) {
            if (!hasRelationships) {
                prompt.append("\n## RELATIONSHIPS\n");
                hasRelationships = true;
            }
            prompt.append("- Has ").append(siblings.size()).append(" ").append(siblings.size() == 1 ? "sibling" : "siblings").append("\n");
        }

        // Current state - simplified
        prompt.append("\n## CURRENT STATE\n");

        // Happiness - simplified
        double happiness = citizen.getHappiness();
        if (happiness > 8.0) {
            prompt.append("- Very happy\n");
        } else if (happiness > 5.0) {
            prompt.append("- Content\n");
        } else {
            prompt.append("- Unhappy\n");
        }

        // Health - only mention if below 50%
        double healthPercent = (citizen.getHealth() / Math.max(1.0, citizen.getMaxHealth())) * 100;
        if (healthPercent < 50) {
            prompt.append("- Feeling weak/in pain\n");
        }

        // Current activity (if available)
        final VisibleCitizenStatus status = citizen.getVisibleStatus();
        if (status != null) {
            prompt.append("- Activity: ").append(formatStatus(status)).append("\n");
        }

        // Concise communication guidance
        prompt.append("\n## GUIDELINES\n");
        prompt.append("- Speak in first person, keep responses brief\n");

        // Player relationship
        var perms = citizen.getColony().getPermissions().getPlayers().get(speakingTo.getUUID());
        if (perms != null) {
            var r = perms.getRank();
            String rankName = r.isHostile() ? "enemy" : (
                    r.isColonyManager() ? "manager" : (
                            r.isInitial() ? "leader" : "visitor"
                    )
            );
            prompt.append("- Address player as ").append(rankName).append("\n");
        }

        // Final instruction
        prompt.append("\nStay in character. Be brief. If unsure, respond with confusion or change subject.");
        prompt.append("\nALWAYS respond in ").append(getLanguageNameFromCode(Config.language));

        return prompt.toString();
    }

    /**
     * Extract and append only the most significant skills to the prompt
     *
     * @param skillHandler the skill handler
     * @param prompt       the StringBuilder to append to
     */
    private static void appendCondensedSkills(ICitizenSkillHandler skillHandler, StringBuilder prompt) {
        Map<Skill, CitizenSkillHandler.SkillData> skills = skillHandler.getSkills();

        // Find top 2 skills
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
            prompt.append("\n## KEY ATTRIBUTES\n");
            prompt.append("- Best at **").append(formatSkillName(highestSkill)).append("** (level ").append(highestLevel).append(")\n");

            // Add a personality trait based on top skill
            if (highestLevel >= 3) {
                switch (highestSkill) {
                    case Intelligence -> prompt.append("- Intellectual and thoughtful\n");
                    case Strength -> prompt.append("- Values physical prowess\n");
                    case Creativity -> prompt.append("- Has artistic mindset\n");
                    case Knowledge -> prompt.append("- Well-read and informative\n");
                    case Dexterity -> prompt.append("- Has nimble hands\n");
                    case Adaptability -> prompt.append("- Flexible and quick to adapt\n");
                }
            }

            if (secondSkill != null && secondLevel >= 2) {
                prompt.append("- Also good at **").append(formatSkillName(secondSkill)).append("**\n");
            }
        }
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
            return "working";
        } else if (status == VisibleCitizenStatus.SLEEP) {
            return "sleeping";
        } else if (status == VisibleCitizenStatus.HOUSE) {
            return "at home";
        } else if (status == VisibleCitizenStatus.RAIDED) {
            return "on alert (raid)";
        } else if (status == VisibleCitizenStatus.MOURNING) {
            return "mourning";
        } else if (status == VisibleCitizenStatus.BAD_WEATHER) {
            return "sheltering";
        } else if (status == VisibleCitizenStatus.SICK) {
            return "ill";
        } else if (status == VisibleCitizenStatus.EAT) {
            return "eating";
        }

        // Default case - use the translation key without the prefix
        String translationKey = status.getTranslationKey();
        if (translationKey.contains(".")) {
            String[] parts = translationKey.split("\\.");
            return parts[parts.length - 1].toLowerCase().replace('_', ' ');
        }

        return translationKey;
    }
}