package me.sshcrack.mc_talking.onboarding;

import me.sshcrack.mc_talking.api.intro.Introduction;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which introduction a player hears next: the first one, in registration order, that they have not
 * heard yet and that is due. Nothing comes before the welcome, which is always first and always due.
 */
public final class IntroductionQueue {
    private IntroductionQueue() {
    }

    public static Optional<Introduction> next(List<Introduction> registered, Set<String> heard, Predicate<Introduction> due) {
        for (Introduction introduction : registered) {
            if (heard.contains(introduction.id())) continue;
            if (due.test(introduction)) return Optional.of(introduction);
        }
        return Optional.empty();
    }
}
