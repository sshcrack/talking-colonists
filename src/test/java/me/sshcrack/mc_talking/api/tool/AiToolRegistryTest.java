package me.sshcrack.mc_talking.api.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class AiToolRegistryTest {
    private static AiTool dummy() {
        return new AiTool() {
            @Override
            public String description() {
                return "test tool";
            }

            @Override
            public JsonObject execute(AiToolContext context, JsonObject parameters) {
                return new JsonObject();
            }
        };
    }

    @Test
    void providerNameIsDeterministicAndRegistrationIsScoped() {
        var registration = AiToolRegistry.register("colonist_errands", "come_here", dummy());
        try {
            assertEquals("colonist_errands:come_here", registration.id());
            assertEquals("tc_16_colonist_errands_come_here", registration.providerName());
            assertEquals("colonist_errands:come_here",
                    AiToolRegistry.findByProviderName(registration.providerName()).id());
        } finally {
            registration.close();
        }
        assertNull(AiToolRegistry.findById("colonist_errands:come_here"));
    }

    @Test
    void duplicateAndInvalidIdsAreRejected() {
        var registration = AiToolRegistry.register("addon", "hello", dummy());
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> AiToolRegistry.register("addon", "hello", dummy()));
        } finally {
            registration.close();
        }
        assertThrows(IllegalArgumentException.class,
                () -> AiToolRegistry.register("Bad Namespace", "hello", dummy()));
    }
}
