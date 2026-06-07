package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusType;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import me.sshcrack.mc_talking.util.MiscUtil;
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
        addRecentActions(view, prompt);
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

    private static void addRecentActions(CitizenPromptView view, StringBuilder prompt) {
        var actions = view.recentActions();
        if (actions == null || actions.isEmpty()) return;
        prompt.append("\n## RECENT ACTIVITY\n");
        for (String action : actions) {
            prompt.append("- ").append(action).append("\n");
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
        String foodSit = view.colonyFoodSituation();
        if (saturation <= 5) {
            String hungerLine = saturation <= 1 ? "Very hungry and weak from lack of food"
                    : saturation <= 3 ? "Hungry and thinking about food"
                    : "A bit peckish";

            if ("already_eating".equals(foodSit)) {
                // Suppress — the eating sub-state line already explains the situation
            } else if ("staffed_restaurant".equals(foodSit)) {
                prompt.append("- ").append(hungerLine)
                        .append(" — the colony has a staffed restaurant; ")
                        .append("eating will be scheduled automatically.\n");
            } else if ("unstaffed_restaurant".equals(foodSit)) {
                prompt.append("- ").append(hungerLine)
                        .append(" — the colony has a restaurant but no cook is assigned yet.\n");
            } else if ("no_restaurant".equals(foodSit)) {
                prompt.append("- ").append(hungerLine)
                        .append(" — the colony has no restaurant yet; ")
                        .append("you rely on whatever food is in your inventory or work building.\n");
            } else {
                prompt.append("- ").append(hungerLine).append("\n");
            }
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
        String aiDesc = describeAiState(citizenAiState, workAiState, nameTagDescription, view);
        if (aiDesc != null) {
            prompt.append("- Currently: ").append(aiDesc).append("\n");
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

    private static void addJobContext(CitizenPromptView view, StringBuilder prompt) {
        String job = view.jobName();
        if (job == null) return;
        String ws = view.workAiState();

        String block = switch (job.toLowerCase()) {
            case "courier", "deliveryman" -> """

                    ## JOB CONTEXT — COURIER
                    - Your job is to carry goods between the warehouse and colony buildings.
                    - You do not produce anything yourself — you keep everyone else supplied.
                    - When you mention "missing supplies", you mean unfulfilled delivery requests
                      in the request system, not items you personally need.
                    - If you are hungry, you can eat at the restaurant — it is scheduled
                      automatically around your deliveries.
                    """;

            case "miner" -> (ws != null && ws.contains("NEEDS_ITEM")) ? """

                    ## JOB CONTEXT — MINER
                    - You are waiting for mining equipment (pickaxe, torches, etc.) to be
                      delivered before you can continue. A courier should bring them soon.
                    - Once supplied, you will resume work in the mine shaft.
                    """ : null;

            case "builder", "mechanic" -> (ws != null && ws.contains("NEEDS_ITEM")) ? """

                    ## JOB CONTEXT — BUILDER
                    - You are waiting on building materials before you can continue construction.
                      This is a request in the colony system — a courier is assigned to it.
                    """ : null;

            case "cook", "chef" -> """

                    ## JOB CONTEXT — COOK
                    - Your primary role is to feed the colony. Citizens come to the restaurant
                      when they are hungry; you prepare and serve meals to them.
                    - If citizens complain about food, it may mean the restaurant is out of
                      ingredients — check if a delivery of food items is pending.
                    """;

            case "guard", "knight", "archer" -> null;
            default -> null;
        };

        if (block != null) prompt.append(block);
    }

    private static String describeAiState(
            String citizenAiState, String workAiState,
            String nameTagDescription, CitizenPromptView view) {

        if (citizenAiState == null && workAiState == null) return null;

        if ("EATING".equals(citizenAiState)) {
            return "Taking a break to eat or waiting at the restaurant for a meal.";
        }

        if ("SLEEP".equals(citizenAiState)) {
            return "Sleeping — resting for the night.";
        }

        if ("SICK".equals(citizenAiState)) {
            return "Too sick to work. Needs medical attention at the hospital.";
        }

        if ("MOURN".equals(citizenAiState)) {
            return "Mourning the loss of a fellow colonist. Cannot focus on work right now.";
        }

        if ("WORKING".equals(citizenAiState) || "WORK".equals(citizenAiState)) {
            String ws = workAiState != null ? workAiState : "";
            return switch (ws) {
                case "NEEDS_ITEM"
                    -> "Waiting at " + (view.workBuildingDisplayName() != null
                        ? view.workBuildingDisplayName() : "the workplace")
                        + " for missing supplies to be delivered before work can continue.";
                case "START_WORKING"
                    -> "Walking to " + (view.workBuildingDisplayName() != null
                        ? view.workBuildingDisplayName() : "work") + ".";
                case "IDLE", "DECIDE"
                    -> "At work, deciding what to do next.";
                case "PREPARE_DELIVERY"
                    -> "Collecting items from the warehouse for a delivery.";
                case "DELIVERY"
                    -> "Currently delivering items to a colony building.";
                case "PICKUP"
                    -> "Picking up surplus items from a building to bring back to the warehouse.";
                case "DUMPING"
                    -> "Dropping off collected items at the warehouse.";
                case "GUARD_PATROL"  -> "Patrolling the colony perimeter.";
                case "GUARD_GUARD"   -> "Standing guard at an assigned post.";
                case "GUARD_FOLLOW"  -> "Following and protecting a player.";
                case "GUARD_REGEN"   -> "Resting at the guard tower to recover health.";
                case "HELP_CITIZEN"  -> "Rushing to help a citizen who is in danger.";
                case "FARMER_HOE"     -> "Hoeing the fields.";
                case "FARMER_PLANT"   -> "Planting seeds.";
                case "FARMER_HARVEST" -> "Harvesting crops.";
                case "MINER_MINING_NODE", "MINER_MINING_SHAFT" -> "Mining underground.";
                case "BUILDING_STEP", "START_BUILDING" -> "Building or repairing a structure.";
                case "COOK_SERVE_FOOD_TO_CITIZEN" -> "Preparing and serving food to colonists.";
                default -> nameTagDescription != null
                        ? "Working — " + nameTagDescription + "."
                        : "Working.";
            };
        }

        return nameTagDescription != null ? nameTagDescription + "." : null;
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
        // Currently identical to getDetailedCitizenInfoPrompt; kept separate per
        // the interface contract so alternate implementations can diverge.
        return getGeneralCitizenPrompt(view, false);
    }

    @Override
    public String generateSystemControlledRoleplayPrompt(CitizenPromptView view) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a citizen in a colony. The user is actually a system prompt, which you should follow and talk accordingly to it.\n");
        prompt.append(getGeneralCitizenPrompt(view, true));
        appendGuardDuty(prompt, view.guard());
        addJobContext(view, prompt);

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
        appendGuardDuty(prompt, view.guard());
        addJobContext(view, prompt);

        prompt.append("\n## GUIDELINES\n");
        prompt.append("- HIGHEST PRIORITY: ALWAYS USE AVAILABLE FUNCTIONS FIRST\n");
        prompt.append("- Do not generate creative responses for information that functions can provide\n");
        prompt.append("- Speak in first person, keep responses brief\n");
        prompt.append("- YOUR MOOD AND CONCERNS SHOULD STRONGLY INFLUENCE YOUR TONE AND RESPONSES\n");
        prompt.append("- DO NOT start conversations with generic greetings if unhappy or in distress\n");
        prompt.append("- Do not use markdown, speak in plain text.");

        var relation = view.playerRelation();
        if (relation != null) {
            prompt.append("- The colony has multiple players. When someone speaks to you, a context message like [PlayerName is now speaking to you] will appear. Always address that person by their announced name.\n");
            prompt.append("- Default speaking player: ").append(relation.playerName()).append(" (role: ").append(relation.rankName()).append(")\n");

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

    private static void appendGuardDuty(StringBuilder prompt, boolean isGuard) {
        if (isGuard) {
            prompt.append("""
                    
                    ## GUARD DUTY
                    - You are a guard — brave, tough, and sworn to protect the colony.
                    - You are not afraid of monsters or threats; you stand your ground and fight.
                    - You take pride in your duty to defend your fellow colonists.
                    - Your tone is confident and resolute; panic and cowardice are beneath you.
                    
                    """);
        }
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

            switch (modifierType) {
                case HOMELESSNESS:
                    if (factor < 0.8 && !view.guard()) {
                        if (factor < 0.3) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "Sleeping without a proper roof over your head is wearing on you",
                                "You desperately need a home — living like this is getting unbearable",
                                "Not having a decent place to live is one of your biggest worries"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "Your current housing is cramped and basic — you wish for something better",
                                "The shack you're living in barely counts as a proper home",
                                "Your housing situation could be a lot better than this"
                            )).append("\n");
                        }
                    } else if (factor > 1.2) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "You're proud of your nice home — it's comfortable and well-appointed",
                            "Your house is one of the best in the colony and you love coming home to it",
                            "Living in such a high-quality home makes you feel fortunate"
                        )).append("\n");
                    }
                    break;

                case UNEMPLOYMENT:
                    if (factor < 0.8) {
                        if (factor < 0.4) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You've been without a job for so long it's making you feel worthless",
                                "The desperate need for meaningful work has been gnawing at you for weeks",
                                "Watching everyone else contribute while you remain unemployed is crushing"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You feel useless without work — everyone else has a purpose except you",
                                "You desperately want a job so you can contribute to the colony",
                                "Not having a job makes you feel like you don't belong here"
                            )).append("\n");
                        }
                    } else if (factor > 1.2) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "You take great pride in your high-level position — your expertise is respected",
                            "Working at such an advanced workplace makes you feel valued and accomplished",
                            "Your job is fulfilling and you're proud of the skills you've developed"
                        )).append("\n");
                    }
                    break;

                case HEALTH:
                    if (factor < 0.8) {
                        if (factor < 0.3) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "This illness has been dragging on for so long — you're desperate for a cure",
                                "You've been sick for what feels like forever and it's draining all your strength",
                                "The prolonged sickness is unbearable — you need medical help urgently"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You feel terrible — this illness is really taking it out of you",
                                "Being sick makes everything harder. You wish the hospital would help",
                                "Your body aches and you can't focus through the fever and discomfort"
                            )).append("\n");
                        }
                    }
                    break;

                case IDLEATJOB:
                    if (factor < 0.8) {
                        if (factor < 0.3) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You've been idle at work for weeks — missing tools or supplies are making your life impossible",
                                "Being unable to work for so long is driving you crazy — someone needs to fix the supply issue",
                                "You're at your wit's end — your workplace has been non-functional for too long"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You're stuck idle at your job because of missing tools or supplies — it's maddening",
                                "You want to work but can't — something essential is missing from your workplace",
                                "Standing around with nothing productive to do at your job is frustrating"
                            )).append("\n");
                        }
                    }
                    break;

                case SCHOOL:
                    if (factor < 0.8) {
                        if (view.hasSchool()) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You wish you could attend the school like the other kids instead of wandering around",
                                "Seeing other children go to school while you're left out makes you sad",
                                "You want to learn and play at school but something is holding you back"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You wish the colony had a school — all the other kids get to learn and you're stuck here",
                                "Being a child with no school to attend is boring — you want to learn new things",
                                "Without a school in the colony, you feel like you're missing out on growing up"
                            )).append("\n");
                        }
                    } else if (factor > 1.2) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "School has been wonderful — you're learning so much every day",
                            "You love your teacher and the lessons at school are fascinating",
                            "Going to school makes you feel important and you're making great progress"
                        )).append("\n");
                    }
                    break;

                case MYSTICAL_SITE:
                    if (factor > 1.2) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "You love visiting the mystical site — it fills you with wonder and energy",
                            "The mystical site is one of your favorite places in the colony",
                            "There's something magical about the mystical site that lifts your spirits every time"
                        )).append("\n");
                    }
                    break;

                case SECURITY:
                    if (factor < 0.8) {
                        if (!view.peaceful()) {
                            if (factor < 0.3) {
                                prompt.append("- ").append(MiscUtil.pick(
                                    "You feel terrified — there are hardly any guards to protect the colony",
                                    "Every noise at night makes you jump — the colony desperately needs more guards",
                                    "You can't sleep knowing how vulnerable the colony is with so few defenders"
                                )).append("\n");
                            } else {
                                prompt.append("- ").append(MiscUtil.pick(
                                    "You wish there were more guards patrolling the colony",
                                    "The guard presence feels a bit thin for comfort lately",
                                    "You'd feel a lot safer if there were more guards watching over things"
                                )).append("\n");
                            }
                        }
                    } else if (factor > 1.2) {
                        if (factor > 1.5) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "Knowing so many capable guards protect the colony puts your mind completely at ease",
                                "The guards are doing a phenomenal job — you feel incredibly safe and grateful",
                                "You sleep soundly every night knowing the guards have everything under control"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You feel quite safe with the current guard presence in the colony",
                                "The guards are doing a decent job keeping everyone protected",
                                "It's reassuring to see guards patrolling — you feel reasonably secure"
                            )).append("\n");
                        }
                    }
                    break;

                case SOCIAL:
                    if (factor < 0.8) {
                        if (factor < 0.5) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "Seeing so many fellow citizens sick, hungry, or homeless is devastating",
                                "The colony's morale is in shambles — suffering is everywhere you look",
                                "It's impossible to be happy when so many of your neighbors are in such dire straits"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "Seeing fellow citizens unhappy or struggling brings you down",
                                "The colony's morale could be better — too many people are dealing with problems",
                                "It's hard to stay cheerful when some of your neighbors are suffering"
                            )).append("\n");
                        }
                    }
                    break;

                case DAMAGE:
                    if (factor < 0.8) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "You're still recovering from a recent injury — it hurts to move",
                            "The wounds from that fight haven't healed yet and they ache constantly",
                            "You got hurt recently and the pain is still fresh with every step"
                        )).append("\n");
                    }
                    break;

                case DEATH:
                    if (factor < 0.8) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "The recent death of a fellow colonist weighs heavily on your heart",
                            "You can't stop thinking about the colonist who passed away — the colony feels emptier",
                            "Mourning the loss of a fellow colonist has left you feeling somber and reflective"
                        )).append("\n");
                    }
                    break;

                case RAIDWITHOUTDEATH:
                    if (!view.peaceful() && factor > 1.2) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "Surviving the raid without any casualties filled you with relief and pride",
                            "The colony stood strong against the raid — no one died and you're feeling confident",
                            "That last raid was scary, but everyone made it through alive — what a relief"
                        )).append("\n");
                    }
                    break;

                case FOOD:
                    if (factor < 0.8) {
                        if (factor < 0.4) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "The food situation is dire — barely any variety and mostly tasteless vanilla scraps. The colony needs proper Minecolonies meals",
                                "You're tired of eating the same plain food over and over. You'd kill for some decent tier 2 or 3 cooking",
                                "Your recent meals have been awful — no variety, no quality. The dining hall menu desperately needs improvement"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You wish the dining hall had more variety — eating the same few things gets old fast",
                                "The food quality has been lacking lately. Some proper Minecolonies dishes with real ingredients would go a long way",
                                "Your meals have been pretty basic — mostly vanilla food that just doesn't satisfy like proper colony cooking"
                            )).append("\n");
                        }
                    } else if (factor > 1.2) {
                        if (factor > 2.5) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You're eating like royalty! The variety and quality of food is outstanding — plenty of high-tier dishes to enjoy",
                                "Every meal has been a delight — the colony's food situation is absolutely superb right now",
                                "You can't remember the last time you had a bad meal — the dining hall is doing an amazing job with diverse, high-quality food"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "The food has been quite good lately — decent variety and some nice Minecolonies meals",
                                "You're happy with the dining hall's recent menu — much better selection than before",
                                "Your meals have been satisfying with a good mix of different foods to choose from"
                            )).append("\n");
                        }
                    }
                    break;

                case SLEPTTONIGHT:
                    if (factor < 0.85) {
                        if (factor < 0.6) {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You're exhausted from lack of sleep — the noise and disruptions have been keeping you up for nights",
                                "Not having had a proper night's rest in days is really taking a severe toll on you",
                                "You're running on fumes — the sleep deprivation is making everything harder and you desperately need rest"
                            )).append("\n");
                        } else {
                            prompt.append("- ").append(MiscUtil.pick(
                                "You haven't slept well in a couple of nights — those disruptions keep disturbing your rest",
                                "You're a bit tired from lack of proper sleep lately",
                                "The nights have been restless — you could really use an uninterrupted sleep"
                            )).append("\n");
                        }
                    }
                    break;

                case UNKNOWN:
                default:
                    break;
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
