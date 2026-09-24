package me.sshcrack.mc_talking.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PersonalityPoolTest {
    @Test
    void everyBuiltInIsReachableWhenAllAreEnabled() {
        Set<PersonalityArchetype> seen = EnumSet.noneOf(PersonalityArchetype.class);
        int size = PersonalityArchetype.values().length;
        for (int i = 0; i < size; i++) {
            int index = i;
            var pick = PersonalityPool.pick(a -> true, List.of(), bound -> {
                assertEquals(size, bound, "all built-ins enabled is today's pool");
                return index;
            });
            seen.add(pick.archetype());
        }
        assertEquals(EnumSet.allOf(PersonalityArchetype.class), seen);
    }

    @Test
    void disabledArchetypesLeaveThePool() {
        Set<PersonalityArchetype> enabled = EnumSet.of(PersonalityArchetype.GRUMP, PersonalityArchetype.CURIOUS);
        List<PersonalityArchetype> picked = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            int index = i;
            picked.add(PersonalityPool.pick(enabled::contains, List.of(), bound -> {
                assertEquals(2, bound);
                return index;
            }).archetype());
        }
        assertEquals(List.of(PersonalityArchetype.GRUMP, PersonalityArchetype.CURIOUS), picked);
    }

    @Test
    void customArchetypesFollowTheBuiltInsAndBlankOnesAreSkipped() {
        var pick = PersonalityPool.pick(a -> a == PersonalityArchetype.STOIC,
                List.of(" ", "Always speaks in rhymes."), bound -> {
                    assertEquals(2, bound);
                    return 1;
                });
        assertNull(pick.archetype());
        assertEquals("Always speaks in rhymes.", pick.custom());
    }

    @Test
    void emptyPoolAssignsNothing() {
        assertNull(PersonalityPool.pick(a -> false, List.of(), bound -> {
            throw new AssertionError("must not draw from an empty pool");
        }));
    }
}
