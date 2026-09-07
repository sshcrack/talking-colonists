package me.sshcrack.mc_talking.internal.api;

import com.google.gson.JsonObject;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.prompt.PromptContribution;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.tool.AiQueryTool;
import me.sshcrack.mc_talking.api.tool.AiToolContext;
import me.sshcrack.mc_talking.api.tool.AiToolRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonCoexistenceContractTest {
    private static final CitizenPromptView VIEW = (CitizenPromptView) Proxy.newProxyInstance(
            CitizenPromptView.class.getClassLoader(),
            new Class<?>[]{CitizenPromptView.class},
            (proxy, method, args) -> null
    );

    @Test
    void twoAddonsCanContributePromptsAndToolsWithoutOwningEachOthersRegistrations() {
        AddonRegistration provider = PromptRuntime.registerProvider("test:base", 0, new CitizenPromptProvider() {
            @Override
            public String getBasicCitizenInfoPrompt(CitizenPromptView view, boolean firstPerson) {
                return "base";
            }

            @Override
            public String generateCitizenRoleplayPrompt(CitizenPromptView view) {
                return "base";
            }

            @Override
            public String getDetailedCitizenInfoPrompt(CitizenPromptView view) {
                return "base";
            }

            @Override
            public String generateConversationalInfoPrompt(CitizenPromptView view) {
                return "base";
            }

            @Override
            public String generateSystemControlledRoleplayPrompt(CitizenPromptView view) {
                return "base";
            }
        });
        AddonRegistration errandsPrompt = PromptRuntime.registerContributor(
                "colonist_errands:delivery",
                100,
                context -> List.of(PromptContribution.observation(
                        "colonist_errands:delivery",
                        "Delivery",
                        "The tracked delivery is complete.")));
        AddonRegistration voyagerPrompt = PromptRuntime.registerContributor(
                "voyager:expedition",
                100,
                context -> List.of(PromptContribution.observation(
                        "voyager:expedition",
                        "Expedition",
                        "The expedition returned safely.")));
        AddonRegistration errandsTool = AiToolRegistry.register(
                "colonist_errands",
                "delivery_status",
                query("delivery"));
        AddonRegistration voyagerTool = AiToolRegistry.register(
                "voyager",
                "expedition_status",
                query("expedition"));

        try {
            String prompt = PromptRuntime.generateCitizenRoleplayPrompt(VIEW);
            int errandsIndex = prompt.indexOf("The tracked delivery is complete.");
            int voyagerIndex = prompt.indexOf("The expedition returned safely.");
            assertTrue(errandsIndex >= 0);
            assertTrue(voyagerIndex > errandsIndex,
                    "same-order contributors should use deterministic namespaced-id ordering");
            assertNotNull(AiToolRuntime.findById("colonist_errands:delivery_status"));
            assertNotNull(AiToolRuntime.findById("voyager:expedition_status"));

            errandsPrompt.close();
            errandsTool.close();

            String afterErrandsClose = PromptRuntime.generateCitizenRoleplayPrompt(VIEW);
            assertFalse(afterErrandsClose.contains("The tracked delivery is complete."));
            assertTrue(afterErrandsClose.contains("The expedition returned safely."));
            assertNull(AiToolRuntime.findById("colonist_errands:delivery_status"));
            assertNotNull(AiToolRuntime.findById("voyager:expedition_status"));
        } finally {
            provider.close();
            errandsPrompt.close();
            voyagerPrompt.close();
            errandsTool.close();
            voyagerTool.close();
        }

        assertNull(AiToolRuntime.findById("colonist_errands:delivery_status"));
        assertNull(AiToolRuntime.findById("voyager:expedition_status"));
    }

    private static AiQueryTool query(String kind) {
        return new AiQueryTool() {
            @Override
            public String description() {
                return "Read " + kind + " status.";
            }

            @Override
            public JsonObject executeQuery(AiToolContext context, JsonObject parameters) {
                JsonObject result = new JsonObject();
                result.addProperty("kind", kind);
                return result;
            }
        };
    }
}
