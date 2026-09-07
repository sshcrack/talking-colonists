package me.sshcrack.mc_talking.api.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import me.sshcrack.mc_talking.internal.tool.AiToolRuntime;
import org.junit.jupiter.api.Test;

class AiToolRegistryTest {
    private static AiQueryTool dummy() {
        return new AiQueryTool() {
            @Override
            public String description() {
                return "test tool";
            }

            @Override
            public JsonObject executeQuery(AiToolContext context, JsonObject parameters) {
                return new JsonObject();
            }
        };
    }

    @Test
    void providerNameIsInternalAndRegistrationIsScoped() {
        var registration = AiToolRegistry.register("colonist_errands", "come_here", dummy());
        try {
            assertEquals("colonist_errands:come_here", registration.id());
            assertFalse(registration.isClosed());
            var runtimeTool = AiToolRuntime.findById("colonist_errands:come_here");
            assertEquals("tc_16_colonist_errands_come_here", runtimeTool.providerName());
            assertEquals(runtimeTool, AiToolRuntime.findByProviderName(runtimeTool.providerName()));
        } finally {
            registration.close();
        }
        assertTrue(registration.isClosed());
        assertNull(AiToolRuntime.findById("colonist_errands:come_here"));
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

    @Test
    void toolMustChooseOneExecutionContractAndObjectSchemaRoot() {
        AiTool metadataOnly = () -> "metadata only";
        assertThrows(IllegalArgumentException.class,
                () -> AiToolRegistry.register("addon", "metadata_only", metadataOnly));

        AiQueryTool invalidSchema = new AiQueryTool() {
            @Override
            public String description() {
                return "bad schema";
            }

            @Override
            public AiToolParameter parameters() {
                return AiToolParameter.string(true);
            }

            @Override
            public JsonObject executeQuery(AiToolContext context, JsonObject parameters) {
                return new JsonObject();
            }
        };
        assertThrows(IllegalArgumentException.class,
                () -> AiToolRegistry.register("addon", "bad_schema", invalidSchema));
    }
}
