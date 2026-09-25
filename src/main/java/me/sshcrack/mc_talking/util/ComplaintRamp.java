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
     * @param enabled             when false, the tier follows only how bad the problem is, as before
     * @param complaintAfterDays  in an established colony, how many times its usual fix time a problem
     *                            may last before a remark becomes a complaint (the harshness)
     * @param demandAfterDays     the same, before a complaint becomes a demand
     * @param youngColonyDays     colony age over which the founding patience fades out
     * @param youngColonyPatience how many times longer problems may last in a brand-new colony; fades
     *                            linearly to 1 over {@code youngColonyDays}, so citizens never flip from
     *                            patient to angry on one day
     */
    public record Settings(boolean enabled, int complaintAfterDays, int demandAfterDays, int youngColonyDays,
                           double youngColonyPatience) {
        public static final double DEFAULT_YOUNG_COLONY_PATIENCE = 3.0;
        public static final Settings DEFAULTS = new Settings(true, 1, 5, 10, DEFAULT_YOUNG_COLONY_PATIENCE);

        public Settings {
            complaintAfterDays = Math.max(0, complaintAfterDays);
            demandAfterDays = Math.max(complaintAfterDays, demandAfterDays);
            youngColonyDays = Math.max(0, youngColonyDays);
            youngColonyPatience = Math.max(1.0, youngColonyPatience);
        }

        public Settings(boolean enabled, int complaintAfterDays, int demandAfterDays, int youngColonyDays) {
            this(enabled, complaintAfterDays, demandAfterDays, youngColonyDays, DEFAULT_YOUNG_COLONY_PATIENCE);
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
        // A problem is only held against the player once it has lasted longer than fixing it takes.
        double allowance = fixDays(type) * patience(colonyAgeDays, settings);
        if (activeDays < settings.complaintAfterDays() * allowance) return Tier.REMARK;
        if (activeDays < settings.demandAfterDays() * allowance) return Tier.COMPLAINT;
        return Tier.DEMAND;
    }

    /**
     * Rough colony days an established colony needs to fix the problem: a residence takes a builder
     * a day or two, a job needs a hut placed and a worker assigned, a sick citizen a healer.
     */
    static double fixDays(@NotNull HappinessModifierType type) {
        return switch (type) {
            case HOMELESSNESS -> 2.0;
            case HEALTH -> 1.5;
            default -> 1.0;
        };
    }

    /**
     * How many times longer than usual problems may last: {@code youngColonyPatience} when the colony
     * is founded, falling linearly to 1 once it is {@code youngColonyDays} old.
     */
    public static double patience(int colonyAgeDays, @NotNull Settings settings) {
        if (!settings.enabled() || settings.youngColonyDays() == 0) return 1.0;
        double youth = Math.max(0.0, 1.0 - (double) colonyAgeDays / settings.youngColonyDays());
        return 1.0 + (settings.youngColonyPatience() - 1.0) * youth;
    }

    /**
     * How much of a mood problem citizens let show, from 1/patience in a brand-new colony up to 1
     * once it is established. A new colony lacks guards and variety because nothing is built yet.
     */
    public static double severity(int colonyAgeDays, @NotNull Settings settings) {
        return 1.0 / patience(colonyAgeDays, settings);
    }

    /** A happiness factor below 1 pulled towards neutral by {@link #severity}; factors of 1 or more are kept. */
    public static double eased(double factor, int colonyAgeDays, @NotNull Settings settings) {
        if (factor >= 1.0) return factor;
        return 1.0 - (1.0 - factor) * severity(colonyAgeDays, settings);
    }

    /** Happiness at or above this is not eased: it is where a settled citizen is content. */
    public static final double CONTENT_HAPPINESS = 7.0;

    /**
     * Overall happiness (0–10) below {@link #CONTENT_HAPPINESS} pulled towards it by {@link #severity}, for
     * the citizen's mood: in a brand-new colony everyone lacks a home and a job, which does not make
     * them hostile yet.
     */
    public static double easedHappiness(double happiness, int colonyAgeDays, @NotNull Settings settings) {
        if (happiness >= CONTENT_HAPPINESS) return happiness;
        return CONTENT_HAPPINESS - (CONTENT_HAPPINESS - happiness) * severity(colonyAgeDays, settings);
    }

    /**
     * Whether the colony is still in its founding days, when citizens are more hopeful than demanding:
     * what is missing has not been neglected yet, it just has not been built.
     */
    public static boolean isYoung(int colonyAgeDays, @NotNull Settings settings) {
        return patience(colonyAgeDays, settings) > 1.0;
    }

    /** The general-unhappiness part of the urgency weight, scaled by {@link #severity}. */
    public static double unhappinessUrgency(double happiness, int colonyAgeDays, @NotNull Settings settings) {
        double weight = happiness < 3.0 ? 1.5 : happiness < 5.0 ? 0.6 : 0;
        return weight * severity(colonyAgeDays, settings);
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
