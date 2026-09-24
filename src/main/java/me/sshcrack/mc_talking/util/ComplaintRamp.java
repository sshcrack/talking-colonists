package me.sshcrack.mc_talking.util;

import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * How strongly a citizen voices a lasting problem (roadmap Q5). The duration comes from
 * MineColonies itself: homelessness, unemployment, sickness and idling at work are time-based
 * happiness modifiers that count the colony days the problem has lasted, reset when it clears and
 * are saved with the citizen. This class only picks wording and urgency; it never changes
 * MineColonies' happiness values.
 */
public final class ComplaintRamp {
    /** Escalation from a passing remark to open demands. */
    public enum Tier {
        REMARK, COMPLAINT, DEMAND;

        public @NotNull Tier atMost(@NotNull Tier cap) {
            return ordinal() <= cap.ordinal() ? this : cap;
        }
    }

    /**
     * @param enabled            when false, the tier follows only how bad the problem is, as before
     * @param complaintAfterDays colony days before a remark becomes a complaint
     * @param demandAfterDays    colony days before a complaint becomes a demand
     * @param youngColonyDays    housing complaints stay remarks while the colony is younger than this
     */
    public record Settings(boolean enabled, int complaintAfterDays, int demandAfterDays, int youngColonyDays) {
        public static final Settings DEFAULTS = new Settings(true, 1, 5, 7);

        public Settings {
            complaintAfterDays = Math.max(0, complaintAfterDays);
            demandAfterDays = Math.max(complaintAfterDays, demandAfterDays);
            youngColonyDays = Math.max(0, youngColonyDays);
        }
    }

    /** Factor below which a modifier counts as a problem, as in the prompt wording. */
    public static final double NEGATIVE_FACTOR = 0.8;
    private static final double SEVERE_FACTOR = 0.3;

    private ComplaintRamp() {
    }

    /**
     * @param factor       the modifier's current factor, used only when the ramp is disabled
     * @param activeDays   colony days the problem has lasted
     * @param colonyAgeDays age of the citizen's colony in days
     */
    public static @NotNull Tier tier(@NotNull HappinessModifierType type, double factor, int activeDays,
                                     int colonyAgeDays, @NotNull Settings settings) {
        if (!settings.enabled()) {
            return factor < SEVERE_FACTOR ? Tier.DEMAND : Tier.COMPLAINT;
        }
        Tier tier;
        if (activeDays < settings.complaintAfterDays()) tier = Tier.REMARK;
        else if (activeDays < settings.demandAfterDays()) tier = Tier.COMPLAINT;
        else tier = Tier.DEMAND;
        if (type == HappinessModifierType.HOMELESSNESS && colonyAgeDays < settings.youngColonyDays()) {
            tier = tier.atMost(Tier.REMARK);
        }
        return tier;
    }

    public static @NotNull Tier tier(@NotNull HappinessModifierView modifier, int colonyAgeDays,
                                     @NotNull Settings settings) {
        return tier(modifier.type(), modifier.factor(), modifier.activeDays(), colonyAgeDays, settings);
    }

    /** The tier of {@code type} if it is currently a problem, else {@code null}. */
    public static @Nullable Tier activeTier(@NotNull List<HappinessModifierView> modifiers,
                                            @NotNull HappinessModifierType type, int colonyAgeDays,
                                            @NotNull Settings settings) {
        for (HappinessModifierView modifier : modifiers) {
            if (modifier.type() == type && modifier.factor() < NEGATIVE_FACTOR) {
                return tier(modifier, colonyAgeDays, settings);
            }
        }
        return null;
    }

    /** Urgent-contact weight for being homeless, scaled by tier (0.7 was the old flat weight). */
    public static double homelessUrgency(@NotNull Tier tier) {
        return switch (tier) {
            case REMARK -> 0.2;
            case COMPLAINT -> 0.7;
            case DEMAND -> 1.0;
        };
    }
}
