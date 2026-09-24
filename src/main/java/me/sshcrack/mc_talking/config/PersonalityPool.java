package me.sshcrack.mc_talking.config;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Picks a personality for a citizen from the enabled built-in archetypes and the custom list. */
public final class PersonalityPool {
    /** Exactly one of the two is set. */
    public record Pick(@Nullable PersonalityArchetype archetype, @Nullable String custom) {
    }

    private PersonalityPool() {
    }

    /**
     * @param randomBelow returns a uniform value in {@code [0, bound)}
     * @return the pick, or {@code null} when every archetype is disabled and no custom one is set
     */
    public static @Nullable Pick pick(Predicate<PersonalityArchetype> enabled, List<String> customs,
                                      IntUnaryOperator randomBelow) {
        List<PersonalityArchetype> builtIns = new ArrayList<>();
        for (PersonalityArchetype archetype : PersonalityArchetype.values()) {
            if (enabled.test(archetype)) builtIns.add(archetype);
        }
        List<String> usableCustoms = customs.stream().filter(c -> c != null && !c.isBlank()).toList();
        int total = builtIns.size() + usableCustoms.size();
        if (total == 0) return null;

        int index = randomBelow.applyAsInt(total);
        if (index < builtIns.size()) return new Pick(builtIns.get(index), null);
        return new Pick(null, usableCustoms.get(index - builtIns.size()));
    }
}
