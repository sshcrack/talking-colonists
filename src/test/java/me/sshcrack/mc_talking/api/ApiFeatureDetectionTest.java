package me.sshcrack.mc_talking.api;

import me.sshcrack.mc_talking.api.service.ContextService;
import me.sshcrack.mc_talking.api.service.ConversationRuleService;
import me.sshcrack.mc_talking.api.service.ConversationService;
import me.sshcrack.mc_talking.api.service.MemoryService;
import me.sshcrack.mc_talking.api.service.PregenerationService;
import me.sshcrack.mc_talking.api.service.PromptService;
import me.sshcrack.mc_talking.api.service.ToolService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves {@link ApiFeature} detection degrades gracefully instead of throwing
 * {@link NoSuchMethodError} when a runtime predates a feature or predates {@link ApiFeature}
 * itself, and that unsupported feature entry points fail with the documented
 * {@link UnsupportedOperationException}.
 */
class ApiFeatureDetectionTest {

    /**
     * Simulates a Talking Colonists 2.0 runtime: a {@code Services} implementation that only ever
     * knew about the fixed service accessors and never overrides {@code apiMinorVersion()} or
     * {@code supports(ApiFeature)}. Because those are default methods on the interface, this
     * pre-2.1 implementation still satisfies the (recompiled) 2.1 {@code Services} contract and
     * answers "unsupported"/"minor 0" instead of failing to link.
     */
    private static final class PredatesApiFeatureServices implements TalkingColonistsApi.Services {
        @Override
        public int apiMajorVersion() {
            return TalkingColonistsApi.API_MAJOR_VERSION;
        }

        @Override
        public @NotNull PromptService prompts() {
            return proxy(PromptService.class);
        }

        @Override
        public @NotNull ConversationRuleService conversationRules() {
            return proxy(ConversationRuleService.class);
        }

        @Override
        public @NotNull PregenerationService pregeneration() {
            return proxy(PregenerationService.class);
        }

        @Override
        public @NotNull ToolService tools() {
            return proxy(ToolService.class);
        }

        @Override
        public @NotNull ContextService context() {
            return proxy(ContextService.class);
        }

        @Override
        public @NotNull ConversationService conversations() {
            return proxy(ConversationService.class);
        }

        @Override
        public @NotNull MemoryService memory() {
            return proxy(MemoryService.class);
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type) {
            return (T) Proxy.newProxyInstance(
                    type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> null);
        }
    }

    @Test
    void servicesWithoutOverridesReportsEveryFeatureUnsupportedAndMinorVersionZero() {
        TalkingColonistsApi.Services predates2point1 = new PredatesApiFeatureServices();

        assertEquals(0, predates2point1.apiMinorVersion());
        for (ApiFeature feature : ApiFeature.values()) {
            assertFalse(
                    predates2point1.supports(feature),
                    () -> "pre-2.1 runtime must report " + feature + " as unsupported");
        }
    }

    @Test
    void realBackendSupportsExactlyTheLandedFeatures() {
        TalkingColonistsApi.Services backend = resolveRealBackend();
        java.util.Set<ApiFeature> landed = java.util.EnumSet.of(ApiFeature.BROADCAST_PUBLISHING);

        assertEquals(TalkingColonistsApi.API_MINOR_VERSION, backend.apiMinorVersion());
        for (ApiFeature feature : ApiFeature.values()) {
            assertEquals(landed.contains(feature), backend.supports(feature),
                    () -> feature + " support must match whether its Track A task has landed");
        }
    }

    @Test
    void minorVersionIsExposed() {
        assertEquals(1, TalkingColonistsApi.API_MINOR_VERSION);
    }

    @Test
    void staticFacadeReportsUnsupportedAgainstTheInstalledRuntime() {
        assertFalse(TalkingColonistsApi.supports(ApiFeature.COLONY_EVENTS));
    }

    @Test
    void requireSupportedThrowsDocumentedExceptionForUnsupportedFeature() {
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> TalkingColonistsApi.requireSupported(ApiFeature.COLONY_EVENTS));
        assertEquals(true, exception.getMessage().contains("COLONY_EVENTS"));
    }

    private static TalkingColonistsApi.Services resolveRealBackend() {
        try {
            Class<?> implementation = Class.forName(
                    "me.sshcrack.mc_talking.internal.api.TalkingColonistsApiBackend");
            Object instance = implementation.getField("INSTANCE").get(null);
            return (TalkingColonistsApi.Services) instance;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Real Services backend must be reachable from tests", e);
        }
    }
}
