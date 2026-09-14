package me.sshcrack.mc_talking.api.tool;

import me.sshcrack.mc_talking.api.registration.NamespacedAddonId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiToolRegistryContractTest {
    @Test
    void providerNameMatchesTheNameAdvertisedToGemini() {
        assertEquals("tc_7_errands_take_job", AiToolRegistry.providerName("errands", "take_job"));
        assertEquals("tc_7_errands_take_job",
                AiToolRegistry.providerName(new NamespacedAddonId("errands", "take_job")));
    }

    @Test
    void providerNameUsesTheSameAddonIdValidationAsRegistration() {
        assertThrows(IllegalArgumentException.class, () -> AiToolRegistry.providerName("Errands", "take_job"));
        assertThrows(IllegalArgumentException.class, () -> AiToolRegistry.providerName("errands", "take-job"));
    }
}
