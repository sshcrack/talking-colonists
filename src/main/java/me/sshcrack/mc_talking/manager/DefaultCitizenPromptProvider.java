package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusType;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation for citizen prompt generation.
 */
public class DefaultCitizenPromptProvider implements CitizenPromptProvider {
    @Override
    public String getBasicCitizenInfoPrompt(@NotNull CitizenPromptView view, boolean firstPerson) {
        StringBuilder prompt = new StringBuilder();
        String name = view.name();
        String citizenType = (view.child() ? "Child" : "Adult") + " " + (view.female() ? "woman" : "man");

        if (firstPerson) {
            prompt.append("# ROLEPLAY AS ").append(name).append("\n\n");
            prompt.append("You: ").append(citizenType);
        } else {
            prompt.append("# CITIZEN INFO ").append(name).append("\n\n");
            prompt.append("Type: ").append(citizenType);
        }

        if (view.jobName() != null) {
            prompt.append(", **").append(view.jobName()).append("**");
            if (view.workBuildingDisplayName() != null) {
                prompt.append(" at ").append(view.workBuildingDisplayName())
                        .append(" (level ").append(view.workBuildingLevel()).append(")");
            }
        } else {
            prompt.append(", **unemployed**");
        }

        var sick = view.sick();
        if (sick) {
            prompt.append(", sick");
        }

        if (view.homeless()) {
            prompt.append(", homeless");
        }

        prompt.append(".\n");
        prompt.append("Colony: **").append(view.colonyName()).append("**");
        if (view.homeBuildingDisplayName() != null && !view.homeless()) {
            prompt.append(" | Home: ").append(view.homeBuildingDisplayName())
                    .append(" (level ").append(view.homeBuildingLevel()).append(")");
        } else if (view.guard() && view.workBuildingDisplayName() != null) {
            prompt.append(" | Home: ").append(view.workBuildingDisplayName())
                    .append(" (level ").append(view.workBuildingLevel())
                    .append(") — your guard post serves as your living quarters");
        }
        prompt.append("\n\n");
        return prompt.toString();
    }

    private String getGeneralCitizenPrompt(@NotNull CitizenPromptView view, boolean firstPerson) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(getBasicCitizenInfoPrompt(view, firstPerson));

        if (view.skills() != null && !view.skills().isEmpty()) {
            appendCondensedSkills(view.skills(), prompt);
        }

        addRelationships(view, prompt);
        addColonyDiplomacy(view, prompt);
        addCurrentState(view, prompt, view.sick());
        addObservations(view, prompt);
        addMemory(view, prompt);

        prompt.append("\n## EMOTIONAL PROFILE\n");

        double happiness = view.happiness();

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

        if (view.sick()) {
            prompt.append("- Occasionally mentions symptoms or discomfort\n");
        }

        if (!view.blockingInteractionMessages().isEmpty()) {
            prompt.append("You can't do anything else until the following issues are resolved (written in first person):\n");
            for (var message : view.blockingInteractionMessages()) {
                prompt.append("- ").append(message);
            }
        }

        // Personality archetype
        if (view.personality() != null) {
            prompt.append("\n## PERSONALITY\n");
            prompt.append(view.personality().getPromptLines()).append("\n");
        } else if (view.customPersonalityText() != null) {
            prompt.append("\n## PERSONALITY\n");
            prompt.append(view.customPersonalityText()).append("\n");
        }

        return prompt.toString();
    }

    private void addMemory(CitizenPromptView view, StringBuilder prompt) {
        var memories = view.memories();
        if (memories != null) {
            prompt.append("\n## MEMORIES\n");
            prompt.append(memories.toPrompt(view.interestedParties()));
        }
    }

    private void addObservations(@NotNull CitizenPromptView view, StringBuilder prompt) {
        StringBuilder obs = new StringBuilder();

        if (view.playerState() != null) {
            obs.append("- The player you are speaking to appears ").append(view.playerState()).append("\n");
        }

        if (view.colonyMilestone() != null) {
            obs.append("- Recently noted: ").append(view.colonyMilestone()).append(" This may come up in conversation.\n");
        }

        if (view.activeItemRequests() != null && !view.activeItemRequests().isEmpty()) {
            obs.append("- You are currently waiting for:\n");
            for (String req : view.activeItemRequests()) {
                obs.append("  - ").append(req).append("\n");
            }
            obs.append("- Since you need these materials, naturally mention what you are waiting for if the player asks how you are doing.\n");
        }

        if (view.activeQuests() != null && !view.activeQuests().isEmpty()) {
            obs.append("- You are currently involved in the following quests:\n");
            for (String q : view.activeQuests()) {
                obs.append("  - ").append(q).append("\n");
            }
            obs.append("- Since you have ongoing quests, you can naturally mention them if the topic comes up.\n");
        }

        if (!obs.isEmpty()) {
            prompt.append("\n## OBSERVATIONS\n").append(obs);
        }
    }

    private void addCurrentState(@NotNull CitizenPromptView view, StringBuilder prompt, boolean sick) {
        prompt.append("\n## CURRENT STATE\n");

        appendDetailedHappinessState(view, prompt);

        double saturation = view.saturation();
        if (saturation <= 1) {
            prompt.append("- Very hungry and weak from lack of food\n");
        } else if (saturation <= 3) {
            prompt.append("- Hungry and thinking about food\n");
        } else if (saturation <= 5) {
            prompt.append("- A bit peckish\n");
        }

        if (view.healthPercent() != null) {
            double healthPercent = view.healthPercent();
            if (healthPercent < 20) {
                prompt.append("- Severely injured, in intense pain\n");
            } else if (healthPercent < 50) {
                prompt.append("- Injured and in pain\n");
            } else if (healthPercent < 75) {
                prompt.append("- Slightly hurt\n");
            } else if (healthPercent == 100) {
                prompt.append("- In perfect health\n");
            }
        }

        if (sick) {
            prompt.append("- Sick and feeling terrible. Needs medical attention\n");
        }

        if (view.homeless()) {
            prompt.append("- Very concerned about not having a home\n");
        }

        if (!view.child() && view.jobName() == null) {
            prompt.append("- Frustrated about not having a job\n");
        }

        final CitizenStatusView status = view.status();
        if (status != null) {
            if (view.peaceful() && status.type() == CitizenStatusType.RAIDED) {
                prompt.append("- Currently: going about the day\n");
            } else {
                prompt.append("- Currently: ").append(formatStatus(status)).append("\n");
            }
        }

        String citizenAiState = view.citizenAiState();
        String workAiState = view.workAiState();
        String nameTagDescription = view.nameTagDescription();
        if (citizenAiState != null || workAiState != null || nameTagDescription != null) {
            prompt.append("- AI state: ");
            if (citizenAiState != null) prompt.append(citizenAiState);
            if (workAiState != null) prompt.append(" / ").append(workAiState);
            if (nameTagDescription != null) prompt.append(" — ").append(nameTagDescription);
            prompt.append("\n");
        }

        if (view.environment() != null) {
            prompt.append("- ").append(view.environment()).append("\n");
        }

        // Post-raid trauma
        if (!view.peaceful()) {
            int traumaDuration = McTalkingConfig.INSTANCE.instance().raidTraumaDurationSeconds;
            if (traumaDuration > 0 && ColonyEventBuffer.isInTrauma(view.colonyId(), traumaDuration)) {
                long sinceMs = ColonyEventBuffer.millisSinceRaid(view.colonyId());
                int lost = ColonyEventBuffer.getLostCitizens(view.colonyId());
                prompt.append("\n## POST-RAID TRAUMA\n");

                if (view.guard()) {
                    if (sinceMs < 5 * 60_000L) {
                        prompt.append("- Adrenaline is still pumping after the fight. You're angry the raid happened, not scared.\n");
                    } else if (sinceMs < 15 * 60_000L) {
                        prompt.append("- You're still wired from the battle, replaying the fight and thinking about how to do better next time.\n");
                    } else {
                        prompt.append("- You've settled down but remain vigilant. Another attack won't catch you off guard.\n");
                    }
                    if (lost > 0) {
                        prompt.append("- Tragically, ").append(lost)
                                .append(" of your fellow colonists didn't survive. You silently vow to protect the rest.\n");
                    }
                } else {
                    if (sinceMs < 5 * 60_000L) {
                        prompt.append("- Your hands are still shaking from the raid that just ended. You feel unsafe and terrified.\n");
                    } else if (sinceMs < 15 * 60_000L) {
                        prompt.append("- The recent raid is still fresh in your mind. You're on edge and jumpy.\n");
                    } else {
                        prompt.append("- You're slowly calming down after the raid, but still feel uneasy.\n");
                    }
                    if (lost > 0) {
                        prompt.append("- Tragically, ").append(lost)
                                .append(" of your fellow colonists didn't survive.")
                                .append("\n");
                    }
                }
            }
        }

        // Recent colony events
        if (!view.recentColonyEvents().isEmpty()) {
            prompt.append("\n## RECENT COLONY EVENTS\n");
            for (String event : view.recentColonyEvents()) {
                prompt.append("- ").append(event).append("\n");
            }
        }
    }

    private static void addRelationships(@NotNull CitizenPromptView view, StringBuilder prompt) {
        StringBuilder relationshipPrompt = new StringBuilder();

        if (view.parentNames() != null && !view.parentNames().isEmpty()) {
            relationshipPrompt.append("- Parents: ").append(String.join(", ", view.parentNames())).append("\n");
        }

        if (view.hasPartner()) {
            relationshipPrompt.append("- In a relationship\n");
        }

        List<String> childNames = view.childNames();
        if (!childNames.isEmpty()) {
            relationshipPrompt.append("- Has ").append(childNames.size()).append(" ").append(childNames.size() == 1 ? "child" : "children")
                    .append(": ").append(String.join(", ", childNames)).append("\n");
        }

        List<String> siblingNames = view.siblingNames();
        if (!siblingNames.isEmpty()) {
            relationshipPrompt.append("- Has ").append(siblingNames.size()).append(" ").append(siblingNames.size() == 1 ? "sibling" : "siblings")
                    .append(": ").append(String.join(", ", siblingNames)).append("\n");
        }

        if (!relationshipPrompt.isEmpty()) {
            prompt.append("\n## RELATIONSHIPS\n");
            prompt.append(relationshipPrompt);
        }
    }

    @Override
    public String getDetailedCitizenInfoPrompt(@NotNull CitizenPromptView view) {
        return getGeneralCitizenPrompt(view, false);
    }

    @Override
    public String generateConversationalInfoPrompt(@NotNull CitizenPromptView view) {
        return getGeneralCitizenPrompt(view, false);
    }

    @Override
    public String generateSystemControlledRoleplayPrompt(CitizenPromptView view) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a citizen in a colony. The user is actually a system prompt, which you should follow and talk accordingly to it.\n");
        prompt.append(getGeneralCitizenPrompt(view, true));

        if (view.guard()) {
            prompt.append("""
                    
                    ## GUARD DUTY
                    - You are a guard — brave, tough, and sworn to protect the colony.
                    - You are not afraid of monsters or threats; you stand your ground and fight.
                    - You take pride in your duty to defend your fellow colonists.
                    - Your tone is confident and resolute; panic and cowardice are beneath you.
                    
                    """);
        }

        prompt.append("""
                        ## GUIDELINES
                        - HIGHEST PRIORITY: ALWAYS USE AVAILABLE FUNCTIONS FIRST
                        - Do not generate creative responses for information that functions can provide
                        - Speak in first person
                        - YOUR MOOD AND CONCERNS SHOULD STRONGLY INFLUENCE YOUR TONE AND RESPONSES
                        - DO NOT start conversations with generic greetings if unhappy or in distress
                        - Do not use markdown, speak in plain text.
                        REMEMBER: ALWAYS check available functions FIRST before answering any question. NEVER make up information that a function can provide.
                        Start by speaking in the language %s and ONLY switch if the user is speaking in another language
                        """.formatted(view.responseLanguageName()));

        return prompt.toString();
    }

    @Override
    public String generateCitizenRoleplayPrompt(@NotNull final CitizenPromptView view) {
        final StringBuilder prompt = new StringBuilder();
        prompt.append(getGeneralCitizenPrompt(view, true));

        if (view.guard()) {
            prompt.append("""
                    
                    ## GUARD DUTY
                    - You are a guard — brave, tough, and sworn to protect the colony.
                    - You are not afraid of monsters or threats; you stand your ground and fight.
                    - You take pride in your duty to defend your fellow colonists.
                    - Your tone is confident and resolute; panic and cowardice are beneath you.
                    
                    """);
        }

        prompt.append("\n## GUIDELINES\n");
        prompt.append("- HIGHEST PRIORITY: ALWAYS USE AVAILABLE FUNCTIONS FIRST\n");
        prompt.append("- Do not generate creative responses for information that functions can provide\n");
        prompt.append("- Speak in first person, keep responses brief\n");
        prompt.append("- YOUR MOOD AND CONCERNS SHOULD STRONGLY INFLUENCE YOUR TONE AND RESPONSES\n");
        prompt.append("- DO NOT start conversations with generic greetings if unhappy or in distress\n");
        prompt.append("- Do not use markdown, speak in plain text.");

        var relation = view.playerRelation();
        if (relation != null) {
            prompt.append("- Address player as ").append(relation.playerName()).append(", he has the role of a ").append(relation.rankName()).append("\n");

            if (relation.hostile()) {
                prompt.append("- Be guarded and suspicious toward the player\n");
            } else if (relation.colonyLeadership()) {
                prompt.append("- Show proper respect to colony leadership\n");
            }
        }

        prompt.append(
                "\nStay in character. Express emotions matching your circumstances. If very unhappy or in pain, make that clear in your tone and content.");
        prompt.append(
                "\nREMEMBER: ALWAYS check available functions FIRST before answering any question. NEVER make up information that a function can provide.");
        prompt.append("\nStart by speaking in the language ").append(view.responseLanguageName()).append(" and ONLY switch if the user is speaking in another language");

        return prompt.toString();
    }

    private static void addColonyDiplomacy(@NotNull CitizenPromptView view, StringBuilder prompt) {
        List<String> connections = view.colonyConnections();
        if (connections != null && !connections.isEmpty()) {
            prompt.append("\n## COLONY DIPLOMACY\n");
            prompt.append("Your colony has relations with neighboring colonies:\n");
            for (String conn : connections) {
                prompt.append("- ").append(conn).append("\n");
            }
        }
    }

    private static void appendDetailedHappinessState(CitizenPromptView view, StringBuilder prompt) {
        double happiness = view.happiness();

        if (happiness > 8.0) {
            prompt.append("- Very happy (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else if (happiness > 5.0) {
            prompt.append("- Content (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else if (happiness > 3.0) {
            prompt.append("- Unhappy (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else {
            prompt.append("- Miserable (").append(String.format("%.1f", happiness)).append("/10)\n");
        }

        for (var modifier : view.happinessModifiers()) {
            HappinessModifierType modifierType = modifier.type();
            double factor = modifier.factor();
            if (factor < 0.8 || factor > 1.2) {
                switch (modifierType) {
                    case HOMELESSNESS:
                        if (factor < 0.8 && !view.guard()) {
                            prompt.append("- Distressed about housing situation\n");
                        }
                        break;
                    case UNEMPLOYMENT:
                        if (factor < 0.8) {
                            prompt.append("- Anxious about employment status\n");
                        }
                        break;
                    case HEALTH:
                        if (factor < 0.8) {
                            prompt.append("- Concerned about health issues\n");
                        }
                        break;
                    case IDLEATJOB:
                        if (factor < 0.8) {
                            prompt.append("- Frustrated by lack of work to do\n");
                        }
                        break;
                    case SCHOOL:
                        if (factor < 0.8) {
                            if (view.hasSchool()) {
                                prompt.append("- Disappointed by lack of school activities\n");
                            } else {
                                prompt.append("- Disappointed by lack of school in the colony\n");
                            }
                        }
                        if (factor > 1.2) {
                            prompt.append("- Enjoying school activities\n");
                        }
                        break;
                    case MYSTICAL_SITE:
                        if (factor < 0.8) {
                            prompt.append("- Disappointed by lack of mystical experiences\n");
                        } else {
                            prompt.append("- Enjoying mystical site visits\n");
                        }
                        break;
                    case SECURITY:
                        if (factor < 0.8) {
                            if (!view.peaceful()) {
                                prompt.append("- Feels unsafe in the colony\n");
                            }
                        } else {
                            prompt.append("- Feels very secure in the colony\n");
                        }
                        break;
                    case SOCIAL:
                        if (factor < 0.8) {
                            prompt.append("- Feeling socially isolated\n");
                        } else {
                            prompt.append("- Enjoying colony social life\n");
                        }
                        break;
                    case DAMAGE:
                        if (factor < 0.8) {
                            prompt.append("- Have been injured recently\n");
                        }
                        break;
                    case DEATH:
                        if (factor < 0.8) {
                            prompt.append("- Distressed by recent death in the colony\n");
                        }
                        break;
                    case RAIDWITHOUTDEATH:
                        if (!view.peaceful() && factor > 1.2) {
                            prompt.append("- Feeling safe because the recent raid was without civilian deaths\n");
                        }
                        break;
                    case FOOD:
                        if (factor < 0.8) {
                            prompt.append("- Unhappy with food quality/variety\n");
                        } else {
                            prompt.append("- Very satisfied with food quality\n");
                        }
                        break;
                    case SLEPTTONIGHT:
                        if (factor < 0.8) {
                            prompt.append("- Tired from lack of sleep\n");
                        }
                        break;
                    case UNKNOWN:
                    default:
                        break;
                }
            }
        }
    }

    private static void appendCondensedSkills(List<SkillLevelView> skillLevels, StringBuilder prompt) {
        Map<String, Integer> skills = skillLevels.stream()
                .collect(Collectors.toMap(SkillLevelView::name, SkillLevelView::level, Math::max));

        String highestSkill = null;
        int highestLevel = -1;
        String secondSkill = null;
        int secondLevel = -1;

        for (Map.Entry<String, Integer> entry : skills.entrySet()) {
            int level = entry.getValue();
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

            if (highestLevel >= 3) {
                switch (highestSkill) {
                    case "Intelligence" -> prompt.append("- Intellectual and thoughtful\n");
                    case "Strength" -> prompt.append("- Values physical prowess\n");
                    case "Creativity" -> prompt.append("- Has artistic mindset\n");
                    case "Knowledge" -> prompt.append("- Well-read and informative\n");
                    case "Dexterity" -> prompt.append("- Has nimble hands\n");
                    case "Adaptability" -> prompt.append("- Flexible and quick to adapt\n");
                    case "Focus" -> prompt.append("- Detail-oriented and methodical\n");
                    case "Mana" -> prompt.append("- Spiritually sensitive\n");
                    case "Athletics" -> prompt.append("- Physically active and energetic\n");
                    case "Agility" -> prompt.append("- Quick and graceful\n");
                    case "Stamina" -> prompt.append("- Has great endurance\n");
                }
            }

            if (secondSkill != null && secondLevel >= 2) {
                prompt.append("- Also good at **").append(formatSkillName(secondSkill)).append("**\n");
            }

            String lowestSkill = null;
            int lowestLevel = Integer.MAX_VALUE;

            for (Map.Entry<String, Integer> entry : skills.entrySet()) {
                int level = entry.getValue();
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

    private static String formatSkillName(String skill) {
        return skill.toLowerCase().replace('_', ' ');
    }
}
