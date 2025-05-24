package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.citizen.citizenhandlers.CitizenSkillHandler;
import me.sshcrack.mc_talking.Config;
import me.sshcrack.mc_talking.mixin.CitizenDataAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.minecolonies.api.util.constant.HappinessConstants.*;

/**
 * Utility class for generating context information about citizens for LLM interactions
 */
public class CitizenContextUtils {

    /**
     * Converts a locale code (like "de-DE") to a language name (like "German")
     *
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
     * @param data the citizen data to generate roleplay context for
     * @return a formatted system prompt for LLM roleplay
     */
    public static String generateCitizenRoleplayPrompt(@NotNull final ICitizenData data, ServerPlayer speakingTo) {
        final StringBuilder prompt = new StringBuilder();

        // Main instruction - more concise
        prompt.append("# ROLEPLAY AS ").append(data.getName()).append("\n\n");
        prompt.append("You: ").append(data.isChild() ? "Child" : "Adult").append(" ")
                .append(data.isFemale() ? "woman" : "man");

        // Job
        var job = data.getJob();
        if (job != null) {
            var jobName = Component.translatable(job.getJobRegistryEntry().getTranslationKey()).getString();
            prompt.append(", **").append(jobName).append("**");
        } else {
            prompt.append(", **unemployed**");
        }

        // Key status in same line
        var sick = data.getCitizenDiseaseHandler().isSick();
        if (sick) {
            prompt.append(", sick");
        }

        if (data.getHomeBuilding() == null) {
            prompt.append(", homeless");
        }

        prompt.append(".\n\n");

        // Skills - only include if available
        if (data.getCitizenSkillHandler() != null) {
            appendCondensedSkills(data.getCitizenSkillHandler(), prompt);
        }

        // Relationships - only include if they exist
        boolean hasRelationships = false;

        // Parents
        final Tuple<String, String> parents = data.getParents();
        if (parents != null && (parents.getA() != null || parents.getB() != null)) {
            if (!hasRelationships) {
                prompt.append("\n## RELATIONSHIPS\n");
                hasRelationships = true;
            }
            prompt.append("- Parents: ");
            if (parents.getA() != null && !parents.getA().isEmpty() && parents.getB() != null && !parents.getB().isEmpty()) {
                prompt.append(parents.getA()).append(", ").append(parents.getB());
            } else if (parents.getA() != null && !parents.getA().isEmpty()) {
                prompt.append(parents.getA());
            } else if (parents.getB() != null && !parents.getB().isEmpty()) {
                prompt.append(parents.getB());
            }
            prompt.append("\n");
        }

        // Partner
        var partnerId = data.getPartner();
        if (partnerId != null) {
            if (!hasRelationships) {
                prompt.append("\n## RELATIONSHIPS\n");
                hasRelationships = true;
            }
            prompt.append("- In a relationship\n");
        }

        // Children and Siblings (simplified)
        final List<Integer> children = data.getChildren();
        if (children != null && !children.isEmpty()) {
            if (!hasRelationships) {
                prompt.append("\n## RELATIONSHIPS\n");
                hasRelationships = true;
            }
            prompt.append("- Has ").append(children.size()).append(" ").append(children.size() == 1 ? "child" : "children").append("\n");
        }

        final List<Integer> siblings = data.getSiblings();
        if (siblings != null && !siblings.isEmpty()) {
            if (!hasRelationships) {
                prompt.append("\n## RELATIONSHIPS\n");
                hasRelationships = true;
            }
            prompt.append("- Has ").append(siblings.size()).append(" ").append(siblings.size() == 1 ? "sibling" : "siblings").append("\n");
        }

        // Current state - enhanced with detailed happiness information
        prompt.append("\n## CURRENT STATE\n");

        // Detailed happiness with specific factors
        appendDetailedHappinessState(data, prompt);

        // Saturation/hunger
        double saturation = data.getSaturation();
        if (saturation <= 1) {
            prompt.append("- Very hungry and weak from lack of food\n");
        } else if (saturation <= 3) {
            prompt.append("- Hungry and thinking about food\n");
        } else if (saturation <= 5) {
            prompt.append("- A bit peckish\n");
        }

        // Health - more detailed
        var entityOpt = data.getEntity();
        if (entityOpt.isPresent()) {
            var entity = entityOpt.get();
            var health = entity.getHealth();
            var maxHealth = entity.getMaxHealth();

            double healthPercent = (health / Math.max(1.0, maxHealth)) * 100;
            if (healthPercent < 20) {
                prompt.append("- Severely injured, in intense pain\n");
            } else if (healthPercent < 50) {
                prompt.append("- Injured and in pain\n");
            } else if (healthPercent < 75) {
                prompt.append("- Slightly hurt\n");
            }
        }

        // Sick status with more emphasis if applicable
        if (sick) {
            prompt.append("- Sick and feeling terrible. Needs medical attention\n");
        }

        // Homeless/Jobless concerns
        if (data.getHomeBuilding() == null) {
            prompt.append("- Very concerned about not having a home\n");
        }

        if (!data.isChild() && data.getJob() == null) {
            prompt.append("- Frustrated about not having a job\n");
        }

        // Current activity (if available)
        final VisibleCitizenStatus status = data.getStatus();
        if (status != null) {
            prompt.append("- Currently: ").append(formatStatus(status, data)).append("\n");
        }

        // Emotional guidance based on state
        prompt.append("\n## EMOTIONAL PROFILE\n");

        var handler = data.getCitizenHappinessHandler();
        double happiness = handler.getHappiness(data.getColony(), data);

        if (happiness > 8.0) {
            prompt.append("- Generally cheerful and friendly\n");
            prompt.append("- Optimistic about the colony's future\n");
            prompt.append("- Likely to be helpful and engaging\n");
        } else if (happiness > 5.0) {
            prompt.append("- Generally neutral in demeanor\n");
            prompt.append("- Moderately satisfied with life in the colony\n");
            prompt.append("- Can be friendly but has some concerns\n");
        } else if (happiness > 3.0) {
            prompt.append("- Visibly unhappy and somewhat irritable\n");
            prompt.append("- Might complain about colony conditions\n");
            prompt.append("- Less interested in small talk, more focused on needs\n");
        } else {
            prompt.append("- Deeply unhappy and possibly hostile\n");
            prompt.append("- Will openly complain and make demands\n");
            prompt.append("- May refuse requests or be uncooperative\n");
        }

        // Personality traits based on skills and state
        if (sick) {
            prompt.append("- Occasionally mentions symptoms or discomfort\n");
        }

        var blockingInteractions = ((CitizenDataAccessor) data)
                .getCitizenChatOptions()
                .values()
                .stream()
                .filter(e -> e.getPriority().getPriority() >= ChatPriority.IMPORTANT.getPriority())
                .toList();

        if (!blockingInteractions.isEmpty()) {
            prompt.append("You can't do anything else until the following issues are resolved (written in first person):\n");
            for (var interaction : blockingInteractions) {
                prompt.append("- ").append(interaction.getInquiry().getString());
            }
        }

        // Concise communication guidance
        prompt.append("\n## GUIDELINES\n");
        prompt.append("- Speak in first person, keep responses brief\n");
        prompt.append("- YOUR MOOD AND CONCERNS SHOULD STRONGLY INFLUENCE YOUR TONE AND RESPONSES\n");
        prompt.append("- DO NOT start conversations with generic greetings if unhappy or in distress\n");

        // Player relationship
        var perms = data.getColony().getPermissions().getPlayers().get(speakingTo.getUUID());
        if (perms != null) {
            var r = perms.getRank();
            String rankName = r.isHostile() ? "enemy" : (
                    r.isColonyManager() ? "manager" : (
                            r.isInitial() ? "leader" : "visitor"
                    )
            );
            prompt.append("- Address player as ").append(rankName).append("\n");

            if (r.isHostile()) {
                prompt.append("- Be guarded and suspicious toward the player\n");
            } else if (r.isColonyManager() || r.isInitial()) {
                prompt.append("- Show proper respect to colony leadership\n");
            }
        }

        // Final instruction
        prompt.append("\nStay in character. Express emotions matching your circumstances. If very unhappy or in pain, make that clear in your tone and content.");
        prompt.append("\nALWAYS respond in ").append(getLanguageNameFromCode(Config.language));
        prompt.append("\nUse function declarations to perform actions if the description matches one of the registered functions.\n");

        return prompt.toString();
    }

    /**
     * Adds detailed happiness information based on specific modifiers
     *
     * @param data   the citizen data
     * @param prompt the StringBuilder to append to
     */
    private static void appendDetailedHappinessState(ICitizenData data, StringBuilder prompt) {
        var handler = data.getCitizenHappinessHandler();
        double happiness = handler.getHappiness(data.getColony(), data);

        // Overall happiness level
        if (happiness > 8.0) {
            prompt.append("- Very happy (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else if (happiness > 5.0) {
            prompt.append("- Content (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else if (happiness > 3.0) {
            prompt.append("- Unhappy (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else {
            prompt.append("- Miserable (").append(String.format("%.1f", happiness)).append("/10)\n");
        }

        // Check for specific happiness modifiers
        List<String> modifiers = handler.getModifiers();

        for (String modifierId : modifiers) {
            IHappinessModifier modifier = handler.getModifier(modifierId);
            if (modifier != null) {
                double factor = modifier.getFactor(data);

                // Only mention significantly negative or positive factors
                if (factor < 0.8 || factor > 1.2) {
                    switch (modifierId) {
                        case HEALTH:
                            if (factor < 0.8) prompt.append("- Concerned about health issues\n");
                            break;
                        case FOOD:
                            if (factor < 0.8) prompt.append("- Unhappy with food quality/variety\n");
                            else prompt.append("- Very satisfied with food quality\n");
                            break;
                        case HOMELESSNESS:
                            if (factor < 0.8) prompt.append("- Distressed about housing situation\n");
                            break;
                        case UNEMPLOYMENT:
                            if (factor < 0.8) prompt.append("- Anxious about employment status\n");
                            break;
                        case SECURITY:
                            if (factor < 0.8) prompt.append("- Feels unsafe in the colony\n");
                            else prompt.append("- Feels very secure in the colony\n");
                            break;
                        case SOCIAL:
                            if (factor < 0.8) prompt.append("- Feeling socially isolated\n");
                            else prompt.append("- Enjoying colony social life\n");
                            break;
                        case SLEPTTONIGHT:
                            if (factor < 0.8) prompt.append("- Tired from lack of sleep\n");
                            break;
                        case IDLEATJOB:
                            if (factor < 0.8) prompt.append("- Frustrated by lack of work to do\n");
                            break;
                    }
                }
            }
        }
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
                    case Focus -> prompt.append("- Detail-oriented and methodical\n");
                    case Mana -> prompt.append("- Spiritually sensitive\n");
                    case Athletics -> prompt.append("- Physically active and energetic\n");
                    case Agility -> prompt.append("- Quick and graceful\n");
                    case Stamina -> prompt.append("- Has great endurance\n");
                }
            }

            if (secondSkill != null && secondLevel >= 2) {
                prompt.append("- Also good at **").append(formatSkillName(secondSkill)).append("**\n");
            }

            // Add lowest skill if it's significantly low to create character depth
            Skill lowestSkill = null;
            int lowestLevel = Integer.MAX_VALUE;

            for (Map.Entry<Skill, CitizenSkillHandler.SkillData> entry : skills.entrySet()) {
                int level = entry.getValue().getLevel();
                if (level < lowestLevel) {
                    lowestSkill = entry.getKey();
                    lowestLevel = level;
                }
            }

            if (lowestSkill != null && lowestLevel < 2 && highestLevel - lowestLevel >= 3) {
                prompt.append("- Struggles with **").append(formatSkillName(lowestSkill)).append("**\n");
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
    private static String formatStatus(VisibleCitizenStatus status, ICitizenData data) {
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
            var deceased = String.join(",", data.getCitizenMournHandler().getDeceasedCitizens());
            return "mourning " + deceased;
        } else if (status == VisibleCitizenStatus.BAD_WEATHER) {
            return "sheltering from bad weather";
        } else if (status == VisibleCitizenStatus.SICK) {
            return "ill and needing care";
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
}