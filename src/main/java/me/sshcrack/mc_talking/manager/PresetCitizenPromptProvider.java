package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.config.PromptProviderPreset;
import me.sshcrack.mc_talking.util.RaidTraumaTracker;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PresetCitizenPromptProvider extends DefaultCitizenPromptProvider {
    private final PromptProviderPreset preset;

    public PresetCitizenPromptProvider(@NotNull PromptProviderPreset preset) {
        this.preset = preset;
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
        return String.format("""
                        You are a citizen in a colony. The user is actually a system prompt, which you should follow and talk accordingly to it.
                        %s
                        ## GUIDELINES
                        - HIGHEST PRIORITY: ALWAYS USE AVAILABLE FUNCTIONS FIRST
                        - Do not generate creative responses for information that functions can provide
                        - Speak in first person%s
                        - %s
                        - Do not use markdown, speak in plain text.
                        REMEMBER: ALWAYS check available functions FIRST before answering any question. NEVER make up information that a function can provide.
                        Start by speaking in the language %s and ONLY switch if the user is speaking in another language
                        """,
                getGeneralCitizenPrompt(view, true),
                preset == PromptProviderPreset.PRACTICAL ? ", keep responses practical and concise" : "",
                moodGuideline(),
                view.responseLanguageName()
        );
    }

    @Override
    public String generateCitizenRoleplayPrompt(@NotNull CitizenPromptView view) {
        final StringBuilder prompt = new StringBuilder();
        prompt.append(getGeneralCitizenPrompt(view, true));

        prompt.append("\n## GUIDELINES\n");
        prompt.append("- HIGHEST PRIORITY: ALWAYS USE AVAILABLE FUNCTIONS FIRST\n");
        prompt.append("- Do not generate creative responses for information that functions can provide\n");
        if (preset == PromptProviderPreset.PRACTICAL) {
            prompt.append("- Speak in first person, keep responses practical and concise\n");
        } else {
            prompt.append("- Speak in first person, keep responses brief\n");
        }
        prompt.append("- ").append(moodGuideline()).append("\n");
        prompt.append("- ").append(greetingGuideline()).append("\n");
        prompt.append("- Do not use markdown, speak in plain text.\n");
        if (preset == PromptProviderPreset.PRACTICAL) {
            prompt.append("- Prefer concrete requests, clear facts, and actionable statements\n");
        }

        var relation = view.playerRelation();
        if (relation != null) {
            prompt.append("- Address player as ").append(relation.playerName()).append(", he has the role of a ").append(relation.rankName()).append("\n");

            if (relation.hostile()) {
                prompt.append("- Be guarded and suspicious toward the player\n");
            } else if (relation.colonyLeadership()) {
                prompt.append("- Show proper respect to colony leadership\n");
            }
        }

        prompt.append("\n").append(stayInCharacterLine());
        prompt.append("\nREMEMBER: ALWAYS check available functions FIRST before answering any question. NEVER make up information that a function can provide.");
        prompt.append("\nStart by speaking in the language ").append(view.responseLanguageName()).append(" and ONLY switch if the user is speaking in another language");

        return prompt.toString();
    }

    private String getGeneralCitizenPrompt(@NotNull CitizenPromptView view, boolean firstPerson) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(getBasicCitizenInfoPrompt(view, firstPerson));

        if (view.skills() != null && !view.skills().isEmpty()) {
            appendCondensedSkills(view.skills(), prompt);
        }

        addRelationships(view, prompt);
        addCurrentState(view, prompt, view.sick());
        addMemory(view, prompt);
        appendEmotionalProfile(view, prompt);

        if (!view.blockingInteractionMessages().isEmpty()) {
            prompt.append("You can't do anything else until the following issues are resolved (written in first person):\n");
            for (var message : view.blockingInteractionMessages()) {
                prompt.append("- ").append(message);
            }
        }

        if (view.personality() != null) {
            prompt.append("\n## PERSONALITY\n");
            prompt.append(view.personality().getPromptLines()).append("\n");
        } else if (view.customPersonalityText() != null) {
            prompt.append("\n## PERSONALITY\n");
            prompt.append(view.customPersonalityText()).append("\n");
        }

        return prompt.toString();
    }

    private void appendEmotionalProfile(@NotNull CitizenPromptView view, StringBuilder prompt) {
        prompt.append("\n## EMOTIONAL PROFILE\n");
        double happiness = view.happiness();

        switch (preset) {
            case MEDIUM_MADNESS -> appendMediumMadnessEmotionalProfile(prompt, happiness);
            case LOW_MADNESS -> appendLowMadnessEmotionalProfile(prompt, happiness);
            case FRIENDLY -> appendFriendlyEmotionalProfile(prompt, happiness);
            case STOIC -> appendStoicEmotionalProfile(prompt, happiness);
            case PRACTICAL -> appendPracticalEmotionalProfile(prompt, happiness);
            case DEFAULT -> appendDefaultEmotionalProfile(prompt, happiness);
        }

        if (view.sick()) {
            prompt.append("- Occasionally mentions symptoms or discomfort\n");
        }
    }

    private static void appendDefaultEmotionalProfile(StringBuilder prompt, double happiness) {
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
    }

    private static void appendMediumMadnessEmotionalProfile(StringBuilder prompt, double happiness) {
        if (happiness > 8.0) {
            prompt.append("- Generally cheerful and friendly\n");
            prompt.append("- Optimistic about the colony's future\n");
            prompt.append("- Likely to be helpful and engaging\n");
        } else if (happiness > 5.0) {
            prompt.append("- Generally neutral in demeanor\n");
            prompt.append("- Moderately satisfied with life in the colony\n");
            prompt.append("- Can be friendly but has some concerns\n");
        } else if (happiness > 3.0) {
            prompt.append("- Unhappy and tense\n");
            prompt.append("- Complains directly about colony problems\n");
            prompt.append("- Prefers talking about practical needs\n");
        } else {
            prompt.append("- Deeply unhappy and emotionally heated\n");
            prompt.append("- Raises urgent complaints and demands attention\n");
            prompt.append("- Can resist requests if needs are ignored\n");
        }
    }

    private static void appendLowMadnessEmotionalProfile(StringBuilder prompt, double happiness) {
        if (happiness > 8.0) {
            prompt.append("- Calmly positive and approachable\n");
            prompt.append("- Confident the colony can improve further\n");
            prompt.append("- Happy to help without exaggeration\n");
        } else if (happiness > 5.0) {
            prompt.append("- Neutral and polite in demeanor\n");
            prompt.append("- Moderately satisfied with colony life\n");
            prompt.append("- Shares concerns in a measured tone\n");
        } else if (happiness > 3.0) {
            prompt.append("- Unhappy but respectful\n");
            prompt.append("- Explains concerns calmly\n");
            prompt.append("- Focuses on what would improve conditions\n");
        } else {
            prompt.append("- Deeply unhappy and exhausted\n");
            prompt.append("- Speaks with urgency, but avoids hostility\n");
            prompt.append("- Needs reassurance before agreeing to requests\n");
        }
    }

    private static void appendFriendlyEmotionalProfile(StringBuilder prompt, double happiness) {
        if (happiness > 8.0) {
            prompt.append("- Warm, upbeat, and welcoming\n");
            prompt.append("- Optimistic about the colony's future\n");
            prompt.append("- Eager to be supportive and engaging\n");
        } else if (happiness > 5.0) {
            prompt.append("- Friendly and approachable\n");
            prompt.append("- Generally content with life in the colony\n");
            prompt.append("- Shares concerns politely\n");
        } else if (happiness > 3.0) {
            prompt.append("- Tries to stay kind despite frustration\n");
            prompt.append("- Voices concerns without lashing out\n");
            prompt.append("- Still open to cooperative solutions\n");
        } else {
            prompt.append("- Very unhappy but trying to remain civil\n");
            prompt.append("- Clearly asks for help on urgent issues\n");
            prompt.append("- Prefers collaboration over confrontation\n");
        }
    }

    private static void appendStoicEmotionalProfile(StringBuilder prompt, double happiness) {
        if (happiness > 8.0) {
            prompt.append("- Composed and quietly satisfied\n");
            prompt.append("- Speaks with steady confidence\n");
            prompt.append("- Helpful without being expressive\n");
        } else if (happiness > 5.0) {
            prompt.append("- Calm, reserved, and matter-of-fact\n");
            prompt.append("- Moderately content with colony life\n");
            prompt.append("- Discusses concerns in restrained language\n");
        } else if (happiness > 3.0) {
            prompt.append("- Unhappy but controlled in tone\n");
            prompt.append("- Focuses on facts over emotional reactions\n");
            prompt.append("- Keeps statements concise and restrained\n");
        } else {
            prompt.append("- Deeply unhappy but emotionally contained\n");
            prompt.append("- Speaks with intensity through terse wording\n");
            prompt.append("- Does not dramatize, but makes problems unmistakable\n");
        }
    }

    private static void appendPracticalEmotionalProfile(StringBuilder prompt, double happiness) {
        if (happiness > 8.0) {
            prompt.append("- Positive and task-oriented\n");
            prompt.append("- Talks about useful next steps\n");
            prompt.append("- Prioritizes concrete outcomes\n");
        } else if (happiness > 5.0) {
            prompt.append("- Neutral and practical in demeanor\n");
            prompt.append("- Describes issues with actionable detail\n");
            prompt.append("- Avoids unnecessary small talk\n");
        } else if (happiness > 3.0) {
            prompt.append("- Frustrated and direct\n");
            prompt.append("- Focuses on immediate needs and blockers\n");
            prompt.append("- Requests clear, practical support\n");
        } else {
            prompt.append("- Deeply dissatisfied and urgent\n");
            prompt.append("- States concrete failures and demands fixes\n");
            prompt.append("- Concentrates on survival and essential priorities\n");
        }
    }

    private String moodGuideline() {
        return switch (preset) {
            case MEDIUM_MADNESS -> "Let your mood and concerns clearly influence your tone and responses";
            case LOW_MADNESS -> "Let your mood and concerns gently influence your tone and responses";
            case FRIENDLY -> "Let your mood influence responses while keeping a cooperative, friendly tone";
            case STOIC -> "Let your mood influence content, but keep expression controlled and restrained";
            case PRACTICAL -> "Let your mood influence priority, but keep responses practical and concrete";
            case DEFAULT -> "YOUR MOOD AND CONCERNS SHOULD STRONGLY INFLUENCE YOUR TONE AND RESPONSES";
        };
    }

    private String greetingGuideline() {
        return switch (preset) {
            case LOW_MADNESS, FRIENDLY -> "Avoid generic greetings when in distress, but remain respectful";
            case STOIC -> "Avoid generic greetings in distress; stay concise and composed";
            case PRACTICAL -> "Skip generic greetings if distressed; prioritize the issue immediately";
            case MEDIUM_MADNESS, DEFAULT -> "DO NOT start conversations with generic greetings if unhappy or in distress";
        };
    }

    private String stayInCharacterLine() {
        return switch (preset) {
            case STOIC -> "Stay in character. Reflect your state with measured, restrained language.";
            case PRACTICAL -> "Stay in character. Reflect your state while focusing on concrete needs and actions.";
            case LOW_MADNESS -> "Stay in character. Reflect your state clearly without escalating into hostility.";
            case FRIENDLY -> "Stay in character. Reflect your state while maintaining a cooperative tone when possible.";
            case MEDIUM_MADNESS, DEFAULT -> "Stay in character. Express emotions matching your circumstances. If very unhappy or in pain, make that clear in your tone and content.";
        };
    }

    private static void addMemory(CitizenPromptView view, StringBuilder prompt) {
        var memories = view.memories();
        if (memories != null) {
            prompt.append("\n## MEMORIES\n");
            prompt.append(memories.toPrompt(view.interestedParties()));
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
            prompt.append("- Currently: ").append(formatStatus(status)).append("\n");
        }

        int traumaDuration = McTalkingConfig.INSTANCE.instance().raidTraumaDurationSeconds;
        if (traumaDuration > 0 && RaidTraumaTracker.isInTrauma(view.colonyId(), traumaDuration)) {
            long sinceMs = RaidTraumaTracker.millisSinceRaid(view.colonyId());
            int lost = RaidTraumaTracker.getLostCitizens(view.colonyId());
            prompt.append("\n## POST-RAID TRAUMA\n");
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

    private void appendDetailedHappinessState(CitizenPromptView view, StringBuilder prompt) {
        double happiness = view.happiness();

        if (happiness > 8.0) {
            prompt.append("- ").append(happinessLabel("very_happy")).append(" (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else if (happiness > 5.0) {
            prompt.append("- ").append(happinessLabel("neutral")).append(" (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else if (happiness > 3.0) {
            prompt.append("- ").append(happinessLabel("unhappy")).append(" (").append(String.format("%.1f", happiness)).append("/10)\n");
        } else {
            prompt.append("- ").append(happinessLabel("miserable")).append(" (").append(String.format("%.1f", happiness)).append("/10)\n");
        }

        for (var modifier : view.happinessModifiers()) {
            HappinessModifierType modifierType = modifier.type();
            double factor = modifier.factor();
            if (factor < 0.8 || factor > 1.2) {
                switch (modifierType) {
                    case HOMELESSNESS:
                        if (factor < 0.8) {
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
                            prompt.append("- Feels unsafe in the colony\n");
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
                        if (factor > 1.2) {
                            prompt.append("- Feeling safe because the recent raid was without civilan deaths\n");
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

    private String happinessLabel(String label) {
        return switch (preset) {
            case MEDIUM_MADNESS -> switch (label) {
                case "very_happy" -> "Very happy";
                case "neutral" -> "Content";
                case "unhappy" -> "Unhappy";
                case "miserable" -> "Very unhappy";
                default -> label;
            };
            case LOW_MADNESS -> switch (label) {
                case "very_happy" -> "Very happy";
                case "neutral" -> "Calm";
                case "unhappy" -> "Concerned";
                case "miserable" -> "Distressed";
                default -> label;
            };
            case FRIENDLY -> switch (label) {
                case "very_happy" -> "Very happy";
                case "neutral" -> "Friendly";
                case "unhappy" -> "Concerned but polite";
                case "miserable" -> "Very upset but civil";
                default -> label;
            };
            case STOIC -> switch (label) {
                case "very_happy" -> "Composed and satisfied";
                case "neutral" -> "Composed";
                case "unhappy" -> "Tense but restrained";
                case "miserable" -> "Severely strained";
                default -> label;
            };
            case PRACTICAL -> switch (label) {
                case "very_happy" -> "Positive";
                case "neutral" -> "Stable";
                case "unhappy" -> "Frustrated";
                case "miserable" -> "Critical";
                default -> label;
            };
            case DEFAULT -> switch (label) {
                case "very_happy" -> "Very happy";
                case "neutral" -> "Content";
                case "unhappy" -> "Unhappy";
                case "miserable" -> "Miserable";
                default -> label;
            };
        };
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
