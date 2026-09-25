package me.sshcrack.mc_talking.manager.prompt;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintContext;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintHistory;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintPrompts;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintTopic;
import me.sshcrack.mc_talking.util.ComplaintRamp;
import me.sshcrack.mc_talking.util.MiscUtil;

import java.util.Locale;

/** The happiness part of the citizen prompt's current state (moved from DefaultCitizenPromptProvider). */
public final class HappinessPromptSection {
    private HappinessPromptSection() {
    }

    /** Mood, and one line per happiness modifier that stands out. */
    public static void append(CitizenPromptView view, StringBuilder prompt, ComplaintRamp.Settings complaints) {
        append(view, prompt, complaints, true);
    }

    /**
     * @param complaintToolEnabled whether {@code raise_concern} is offered; without it the model would say
     *                             the call out loud, so the hint is left out
     */
    public static void append(CitizenPromptView view, StringBuilder prompt, ComplaintRamp.Settings complaints,
                              boolean complaintToolEnabled) {
        double happiness = view.wellbeing().happiness();

        if (happiness > 8.0) {
            prompt.append("- Very happy (").append(String.format(Locale.ROOT, "%.1f", happiness)).append("/10)\n");
        } else if (happiness > 5.0) {
            prompt.append("- Content (").append(String.format(Locale.ROOT, "%.1f", happiness)).append("/10)\n");
        } else if (happiness > 3.0) {
            prompt.append("- Unhappy (").append(String.format(Locale.ROOT, "%.1f", happiness)).append("/10)\n");
        } else {
            prompt.append("- Miserable (").append(String.format(Locale.ROOT, "%.1f", happiness)).append("/10)\n");
        }

        prompt.append("\nNOTE: A building's style (cavern, medieval, etc.) is the colony's chosen aesthetic and is NOT a sign of poor quality. Base housing satisfaction only on building level and these factors below, never complain about style.\n\n");

        // A new colony lacks guards, variety and comfort because nothing is built yet, not because the
        // player neglected it: mood problems show less while it is young (fading out, never flipping),
        // and nobody fears raids before one really happened.
        int age = view.colony().ageDays();
        boolean raided = view.colony().lastRaidEndTimeTicks() != null;
        ComplaintContext history = view instanceof ComplaintContext.Holder holder ? holder.complaints() : null;
        for (var modifier : view.wellbeing().happinessModifiers()) {
            var modifierType = modifier.type();
            double factor = modifier.factor();
            int lineStart = prompt.length();

            switch (modifierType) {
                case HOMELESSNESS:
                    if (factor < ComplaintRamp.NEGATIVE_FACTOR && !view.identity().guard()) {
                        appendHousingComplaint(prompt, view.wellbeing().homeless(),
                                ComplaintRamp.tier(modifier, view.colony().ageDays(), complaints));
                    } else if (factor > 1.2) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "You're proud of your nice home — it's comfortable and well-appointed",
                            "Your house is one of the best in the colony and you love coming home to it",
                            "Living in such a high-quality home makes you feel fortunate"
                        )).append("\n");
                    }
                    break;

                case UNEMPLOYMENT:
                    if (factor < ComplaintRamp.NEGATIVE_FACTOR) {
                        prompt.append("- ").append(switch (ComplaintRamp.tier(modifier, view.colony().ageDays(), complaints)) {
                            case REMARK -> MiscUtil.pick(
                                "You'd like to be given a job soon so you can help out",
                                "You're keen to find some work in the colony",
                                "You're waiting to be assigned a job and hope it happens soon");
                            case COMPLAINT -> MiscUtil.pick(
                                "You feel useless without work — everyone else has a purpose except you",
                                "You really want a job so you can contribute to the colony",
                                "Not having a job makes you feel like you don't belong here");
                            case DEMAND -> MiscUtil.pick(
                                "You've been without a job for a long while and really want to find your place",
                                "The need for meaningful work has been gnawing at you for days",
                                "You'd love to contribute like everyone else and hope a job comes along soon");
                        }).append("\n");
                    } else if (factor > 1.2) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "You take great pride in your high-level position — your expertise is respected",
                            "Working at such an advanced workplace makes you feel valued and accomplished",
                            "Your job is fulfilling and you're proud of the skills you've developed"
                        )).append("\n");
                    }
                    break;

                case HEALTH:
                    if (factor < ComplaintRamp.NEGATIVE_FACTOR) {
                        prompt.append("- ").append(switch (ComplaintRamp.tier(modifier, view.colony().ageDays(), complaints)) {
                            case REMARK -> MiscUtil.pick(
                                "You're feeling a bit under the weather",
                                "You think you're coming down with something",
                                "You don't feel quite well today");
                            case COMPLAINT -> MiscUtil.pick(
                                "You feel terrible — this illness is really taking it out of you",
                                "Being sick makes everything harder. You wish the hospital would help",
                                "Your body aches and you can't focus through the fever and discomfort");
                            case DEMAND -> MiscUtil.pick(
                                "This illness has been dragging on for so long — you're desperate for a cure",
                                "You've been sick for days and it's draining all your strength",
                                "The long sickness is wearing you out — you really need a healer");
                        }).append("\n");
                    }
                    break;

                case IDLE_AT_JOB:
                    if (factor < ComplaintRamp.NEGATIVE_FACTOR) {
                        prompt.append("- ").append(switch (ComplaintRamp.tier(modifier, view.colony().ageDays(), complaints)) {
                            case REMARK -> MiscUtil.pick(
                                "Something you need at work is missing, so you're waiting around for now",
                                "Work has stalled for the moment — you're missing tools or supplies",
                                "You can't get on with your job right now because something is missing");
                            case COMPLAINT -> MiscUtil.pick(
                                "You're stuck idle at your job because of missing tools or supplies — it's maddening",
                                "You want to work but can't — something essential is missing from your workplace",
                                "Standing around with nothing productive to do at your job is frustrating");
                            case DEMAND -> MiscUtil.pick(
                                "You've been idle at work for days — the missing tools or supplies keep you from doing your job",
                                "You've been unable to work for a long while and hope the supply issue gets sorted out",
                                "Your workplace has been stuck for a long time and you'd really like to get going again");
                        }).append("\n");
                    }
                    break;

                case SCHOOL:
                    if (factor < 0.8) {
                        if (view.wellbeing().hasSchool()) {
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
                        double felt = ComplaintRamp.eased(factor, age, complaints);
                        if (!view.colony().peaceful() && (raided ? felt < 0.8 : felt < 0.6)) {
                            if (felt < 0.3 && raided) {
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
                    if (ComplaintRamp.eased(factor, age, complaints) < 0.8) {
                        if (ComplaintRamp.eased(factor, age, complaints) < 0.5) {
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

                case RAID_WITHOUT_DEATH:
                    if (!view.colony().peaceful() && factor > 1.2) {
                        prompt.append("- ").append(MiscUtil.pick(
                            "Surviving the raid without any casualties filled you with relief and pride",
                            "The colony stood strong against the raid — no one died and you're feeling confident",
                            "That last raid was scary, but everyone made it through alive — what a relief"
                        )).append("\n");
                    }
                    break;

                case FOOD:
                    if (ComplaintRamp.eased(factor, age, complaints) < 0.8) {
                        if (ComplaintRamp.eased(factor, age, complaints) < 0.4) {
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

                case SLEPT_TONIGHT:
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

                case QUEST, GREAT_FOOD, UNKNOWN:
                    break;
            }
            // How often this problem was raised with the player sets the tone of the next mention.
            if (history != null && factor < 1.0 && prompt.length() > lineStart) {
                ComplaintHistory.Note note = history.note(ComplaintTopic.of(modifierType));
                if (note != null) prompt.append(ComplaintPrompts.note(note));
            }
        }
        if (history != null) {
            for (ComplaintHistory.Residue residue : history.residues()) prompt.append(ComplaintPrompts.residue(residue));
            // Only while the tool is there: without it, the model would say the call out loud.
            if (!history.notes().isEmpty() && complaintToolEnabled) {
                prompt.append(ComplaintPrompts.TOOL_HINT);
            }
        }
    }

    private static void appendHousingComplaint(StringBuilder prompt, boolean homeless, ComplaintRamp.Tier tier) {
        String line;
        if (homeless) {
            line = switch (tier) {
                case REMARK -> MiscUtil.pick(
                    "You don't have a home yet — you hope one gets built for you soon",
                    "You're still waiting for a place to live, but you understand the colony is busy",
                    "A home of your own would be nice once there's time to build one");
                case COMPLAINT -> MiscUtil.pick(
                    "Sleeping without a proper roof over your head is wearing on you",
                    "Not having a place to live is one of your biggest worries",
                    "You'd really like a home — being without one is getting hard");
                case DEMAND -> MiscUtil.pick(
                    "You desperately need a home — living without one is wearing you down",
                    "You've gone without a home for days and it's getting hard",
                    "Being homeless for this long is wearing you down — you want it fixed");
            };
        } else {
            line = switch (tier) {
                case REMARK -> MiscUtil.pick(
                    "Your home is basic, but it will do for now",
                    "Your house is small — maybe it can be improved some day",
                    "Your home is simple, which is fine while the colony is growing");
                case COMPLAINT -> MiscUtil.pick(
                    "Your current housing is cramped and basic — you wish for something better",
                    "The house you're living in could really use an upgrade",
                    "Your housing situation could be a lot better than this");
                case DEMAND -> MiscUtil.pick(
                    "You've put up with cramped housing for a long time and want it improved",
                    "The shack you're living in barely counts as a proper home",
                    "Your poor housing has bothered you for days — it needs an upgrade");
            };
        }
        prompt.append("- ").append(line).append("\n");
    }
}
