package me.sshcrack.mc_talking.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.ai.JobStatus;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility for assessing citizen needs.
 *
 * <p>Consolidates the urgency weight ({@link #calculateUrgencyWeight}) and need
 * signature ({@link #computeNeedSignature}) logic that was previously split
 * across {@link me.sshcrack.mc_talking.ServerEventHandler} and
 * {@link me.sshcrack.mc_talking.ConversationManager}. Both methods iterate the
 * same citizen data with the same thresholds — this class is the single source
 * of truth for need assessment.</p>
 */
public class CitizenNeedAssessor {
    private CitizenNeedAssessor() {
    }

    /**
     * Calculates an urgency weight for a citizen based on their current state.
     * A higher weight means the citizen is more likely to initiate contact with a player.
     * Returns 0 if the citizen has no pressing concerns.
     */
    public static double calculateUrgencyWeight(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        if (data == null)
            return 0;

        double weight = 0;

        double happiness = data.getCitizenHappinessHandler().getHappiness(data.getColony(), data);
        if (happiness < 3.0) {
            weight += 1.5;
        } else if (happiness < 5.0) {
            weight += 0.6;
        }

        if (data.getCitizenDiseaseHandler().isSick()) {
            weight += 0.8;
        }

        if (data.getHomeBuilding() == null && !CitizenHelper.isCitizenGuard(citizen)) {
            double homelessWeight = 0.7;
            if (McTalkingConfig.INSTANCE.instance().scaleHousingComplaintsByAge) {
                int colonyDay = data.getColony().getDay();
                if (colonyDay < 7) {
                    homelessWeight = 0.3;
                }
            }
            weight += homelessWeight;
        }

        double saturation = data.getSaturation();
        if (saturation <= 1) {
            weight += 1.0;
        } else if (saturation <= 3) {
            weight += 0.4;
        }

        var entityOpt = data.getEntity();
        if (entityOpt.isPresent()) {
            double healthPercent = (entityOpt.get().getHealth() / Math.max(1.0, entityOpt.get().getMaxHealth())) * 100.0;
            if (healthPercent < 25.0) {
                weight += 1.0;
            } else if (healthPercent < 50.0) {
                weight += 0.4;
            }
        }

        if (data.getJobStatus() == JobStatus.STUCK) {
            weight += McTalkingConfig.INSTANCE.instance().blockingTaskUrgencyMultiplier;
        }

        return weight;
    }

    /**
     * Returns {@code true} if the citizen has any urgent needs (weight &gt; 0).
     */
    public static boolean hasUrgentNeeds(AbstractEntityCitizen citizen) {
        return calculateUrgencyWeight(citizen) > 0;
    }

    /**
     * Computes a signature string representing the citizen's current urgent needs.
     * If this signature changes between sessions, the cooldown is cleared so the
     * citizen can immediately contact about a new problem.
     */
    public static String computeNeedSignature(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        if (data == null)
            return "none";

        List<String> needs = new ArrayList<>();

        if (data.getJobStatus() == JobStatus.STUCK)
            needs.add("stuck");
        if (data.getCitizenDiseaseHandler().isSick())
            needs.add("sick");

        double saturation = data.getSaturation();
        if (saturation <= 1) {
            needs.add("starving");
        } else if (saturation <= 3) {
            needs.add("hungry");
        }

        if (data.getHomeBuilding() == null)
            needs.add("homeless");

        double happiness = data.getCitizenHappinessHandler().getHappiness(data.getColony(), data);
        if (happiness < 3.0) {
            needs.add("very_unhappy");
        } else if (happiness < 5.0) {
            needs.add("unhappy");
        }

        var entityOpt = data.getEntity();
        if (entityOpt.isPresent()) {
            double healthPercent = (entityOpt.get().getHealth() / Math.max(1.0, entityOpt.get().getMaxHealth())) * 100.0;
            if (healthPercent < 25.0) {
                needs.add("low_health");
            } else if (healthPercent < 50.0) {
                needs.add("medium_health");
            }
        }

        if (needs.isEmpty())
            needs.add("none");
        return String.join(",", needs);
    }
}
