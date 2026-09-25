package me.sshcrack.mc_talking.util;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintHistory;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintPrompts;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintTopic;
import me.sshcrack.mc_talking.conversations.complaints.Complaints;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The prompt for a citizen who walks up to a player with an urgent need: being stuck at work, sickness,
 * hunger, no home, misery or injury, most pressing first.
 */
public final class UrgentContactPrompts {
    private UrgentContactPrompts() {
    }

    /**
     * The prompt, with how often this citizen already raised the problem with the player. Counts as
     * raising it: the citizen is about to say it.
     */
    public static String build(AbstractEntityCitizen citizen, String playerName, @Nullable UUID playerId) {
        String text = build(citizen, playerName);
        ICitizenData data = citizen.getCitizenData();
        ComplaintTopic topic = topic(citizen);
        if (data == null || topic == null || playerId == null) return text;
        ComplaintHistory.Note note = Complaints.note(data, topic, playerId);
        Complaints.recordRaised(data, topic, playerId);
        return note == null ? text : text + ComplaintPrompts.urgent(note);
    }

    /** The problem {@link #build} would walk up about, or null for general misery, injury or no data. */
    public static @Nullable ComplaintTopic topic(AbstractEntityCitizen citizen) {
        ICitizenData data = citizen.getCitizenData();
        if (data == null) return null;
        if (data.getJobStatus() == JobStatus.STUCK) return ComplaintTopic.SUPPLIES;
        if (data.getCitizenDiseaseHandler().isSick()) return ComplaintTopic.HEALTH;
        if (data.getSaturation() <= 1) return ComplaintTopic.FOOD;
        if (data.getHomeBuilding() == null && !CitizenHelper.isCitizenGuard(citizen)) return ComplaintTopic.HOUSING;
        return null;
    }

    public static String build(AbstractEntityCitizen citizen, String playerName) {
        if (citizen.getCitizenData() == null) return buildGenericUrgentPrompt(playerName);

        var data = citizen.getCitizenData();

        // ── CRITICAL: blocking task (STUCK) ──────────────
        if (data.getJobStatus() == JobStatus.STUCK) {
            String jobName = data.getJob() != null
                    ? Component.translatable(data.getJob().getJobRegistryEntry().getTranslationKey()).getString()
                    : "worker";
            String needed = collectNeededItems(data);
            if (!needed.isEmpty()) {
                return format(playerName, MiscUtil.pick(
                        "You're completely stuck and can't do your work as a " + jobName + ". You need: " + needed + ". Call out to %s by name and urgently explain you're blocked and need help.",
                        "You can't continue your job as a " + jobName + " because you're missing something important: " + needed + ". Call out to %s by name and ask for assistance.",
                        "As a " + jobName + ", you're stuck waiting for supplies: " + needed + ". Call out to %s by name and plead for help to get back to work."
                ));
            }

            return format(playerName, MiscUtil.pick(
                    "You're completely stuck and can't do your work as a " + jobName + ". Call out to %s by name and urgently explain what's wrong and ask for help.",
                    "You can't continue your job as a " + jobName + " because you're blocked by missing supplies. Call out to %s by name and ask for assistance."
            ));
        }

        // ── CRITICAL: sickness ───────────────────────────
        if (data.getCitizenDiseaseHandler().isSick()) {
            return format(playerName, MiscUtil.pick(
                    "You are sick and feel terrible. Call out to %s by name and urgently ask for medicine or help.",
                    "You feel awful and can't ignore it anymore. Call out to %s by name and beg for help.",
                    "You're getting worse. Call out to %s by name and urgently ask for treatment."
            ));
        }

        // ── CRITICAL: starvation ─────────────────────────
        double saturation = data.getSaturation();
        if (saturation <= 1) {
            return format(playerName, MiscUtil.pick(
                    "You are starving and weak. Call out to %s by name and beg for food.",
                    "You can barely keep going from hunger. Call out to %s by name for something to eat.",
                    "You're desperate for food. Call out to %s by name and plead for help."
            ));
        }

        var complaints = McTalkingConfig.INSTANCE.instance().complaintRampSettings();
        int colonyAge = data.getColony().getDay();

        // ── CRITICAL: no home ────────────────────────────
        if (data.getHomeBuilding() == null && !CitizenHelper.isCitizenGuard(citizen)) {
            // Until it has lasted longer than building a home takes, it is a hope, not an emergency.
            if (CitizenNeedAssessor.homelessTier(data) == ComplaintRamp.Tier.REMARK) {
                return format(playerName, MiscUtil.pick(
                        "The colony is brand new and you don't have a home yet. Call out to %s by name and cheerfully ask when houses might go up.",
                        "You're excited about the new colony and hoping for a place of your own. Call out to %s by name and share that hope.",
                        "Call out to %s by name, tell them you're glad to be part of the new colony, and ask whether a home for you is planned."
                ));
            }
            return format(playerName, MiscUtil.pick(
                    "You have nowhere to sleep. Call out to %s by name and urgently ask for help.",
                    "You're distressed about having no home. Call out to %s by name and plead for shelter.",
                    "You can't keep going like this without a place to rest. Call out to %s by name."
            ));
        }

        // ── LOW HAPPINESS ────────────────────────────────
        // Unhappiness in a new colony comes from what is not built yet: it shows less while the colony is young.
        double happiness = data.getCitizenHappinessHandler().getHappiness(data.getColony(), data);
        double felt = 10.0 - (10.0 - happiness) * ComplaintRamp.severity(colonyAge, complaints);
        if (felt < 3.0) {
            return format(playerName, MiscUtil.pick(
                    "You're miserable and can't hold it in anymore. Call out to %s by name and voice your frustration.",
                    "You've had enough. Call out to %s by name and demand something change.",
                    "You're deeply unhappy. Call out to %s by name and explain what's wrong."
            ));
        }

        // ── LOW HEALTH ───────────────────────────────────
        var entity = data.getEntity();
        if (entity.isPresent()) {
            double healthPercent = (entity.get().getHealth() / Math.max(1.0, entity.get().getMaxHealth())) * 100.0;

            if (healthPercent < 25.0) {
                return format(playerName, MiscUtil.pick(
                        "You're badly injured and in pain. Call out to %s by name and ask for help.",
                        "You're struggling to stay on your feet. Call out to %s by name urgently.",
                        "You're hurt and need help. Call out to %s by name right now."
                ));
            }
        }

        return buildGenericUrgentPrompt(playerName);
    }

    /**
     * Queries the citizen's work building for open requests and returns a comma-separated
     * list of the items/equipment they are waiting for. Returns empty string if nothing found.
     * Limited to the first 5 items to keep the prompt bounded.
     */
    private static String collectNeededItems(ICitizenData data) {
        IBuilding workBuilding = data.getWorkBuilding();
        if (workBuilding == null) return "";

        Collection<IRequest<?>> openRequests = workBuilding.getOpenRequests(data.getId());
        if (openRequests == null || openRequests.isEmpty()) return "";

        List<String> items = new ArrayList<>();
        for (IRequest<?> req : openRequests) {
            if (items.size() >= 5) {
                items.add("...and more");
                break;
            }
            String display = req.getShortDisplayString().getString();
            if (display != null && !display.isEmpty()) {
                items.add(display);
            }
        }

        if (items.isEmpty()) return "";
        return String.join(", ", items);
    }

    private static String format(String playerName, String template) {
        return String.format(
                template + " Keep it brief — two or three sentences.",
                playerName
        );
    }

    private static String buildGenericUrgentPrompt(String playerName) {
        return String.format(
                "You notice %s nearby and feel the need to speak. Call out to them by name and say what's on your mind. Keep it brief — two or three sentences.",
                playerName
        );
    }
}
