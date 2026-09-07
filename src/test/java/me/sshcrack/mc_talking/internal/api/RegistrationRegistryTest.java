package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.internal.registration.RegistrationRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationRegistryTest {
    @Test
    void orderingIsPriorityThenNamespacedId() {
        var registry = new RegistrationRegistry<String>("test extension");
        var late = registry.register("zeta:addon", 20, "late");
        var beta = registry.register("beta:addon", 10, "beta");
        var alpha = registry.register("alpha:addon", 10, "alpha");
        try {
            assertEquals(
                    List.of("alpha:addon", "beta:addon", "zeta:addon"),
                    registry.orderedSnapshot().stream().map(RegistrationRegistry.Entry::id).toList()
            );
        } finally {
            late.close();
            beta.close();
            alpha.close();
        }
    }

    @Test
    void closeIsExactIdempotentOwnership() {
        var registry = new RegistrationRegistry<String>("test extension");
        var first = registry.register("addon:hook", 0, "first");
        assertNotNull(registry.find("addon:hook"));
        first.close();
        assertTrue(first.isClosed());
        assertNull(registry.find("addon:hook"));

        var replacement = registry.register("addon:hook", 0, "replacement");
        try {
            first.close();
            assertEquals("replacement", registry.find("addon:hook").value());
        } finally {
            replacement.close();
        }
    }

    @Test
    void invalidAndDuplicateIdsAreRejectedConsistently() {
        var registry = new RegistrationRegistry<String>("test extension");
        var registration = registry.register("addon:hook", 0, "value");
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> registry.register("addon:hook", 1, "duplicate"));
        } finally {
            registration.close();
        }
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("Bad Namespace:hook", 0, "bad"));
    }
}
