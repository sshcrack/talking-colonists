package me.sshcrack.mc_talking.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class MiscUtil {
    private static final ThreadLocal<Boolean> FIRST_PICKS = ThreadLocal.withInitial(() -> false);

    public static String describeTime(long dayTime) {
        if (dayTime < 1000) return "early morning (sunrise)";
        if (dayTime < 6000) return "morning";
        if (dayTime < 9000) return "midday";
        if (dayTime < 12000) return "afternoon";
        if (dayTime < 13000) return "sunset";
        if (dayTime < 18000) return "night";
        if (dayTime < 22000) return "late night";
        return "pre-dawn";
    }

    public static String pick(String... options) {
        if (FIRST_PICKS.get()) return options[0];
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }

    /** Runs {@code action} with {@link #pick} always returning its first option, so prompt text is reproducible. */
    public static <T> T withFirstPicks(Supplier<T> action) {
        boolean previous = FIRST_PICKS.get();
        FIRST_PICKS.set(true);
        try {
            return action.get();
        } finally {
            FIRST_PICKS.set(previous);
        }
    }
}
