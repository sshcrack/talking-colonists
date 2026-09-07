package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;

import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.prompt.PromptContribution;
import me.sshcrack.mc_talking.api.prompt.PromptSessionContext;
import me.sshcrack.mc_talking.api.prompt.PromptTarget;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptRuntimeTest {
    private static final CitizenPromptView VIEW = (CitizenPromptView) Proxy.newProxyInstance(
            CitizenPromptView.class.getClassLoader(),
            new Class<?>[]{CitizenPromptView.class},
            (proxy, method, args) -> null
    );

    private final List<AddonRegistration> registrations = new ArrayList<>();

    @AfterEach
    void closeRegistrations() {
        for (int i = registrations.size() - 1; i >= 0; i--) {
            registrations.get(i).close();
        }
        registrations.clear();
    }

    @Test
    void providerAndTwoContributorsCoexistInStableOrder() {
        registerProvider("test:provider", 100, provider("provider-base"));
        registerContributor("test:later", 20, context -> List.of(
                PromptContribution.recollection("test:later", "Later", "later-text")));
        registerContributor("test:earlier", 10, context -> List.of(
                PromptContribution.observation("test:earlier", "Earlier", "earlier-text")));

        String prompt = PromptRuntime.generateCitizenRoleplayPrompt(VIEW);

        int provider = prompt.indexOf("provider-base:CITIZEN_ROLEPLAY");
        int earlier = prompt.indexOf("earlier-text");
        int later = prompt.indexOf("later-text");
        int priorityBoundary = prompt.indexOf("## CORE INSTRUCTION PRIORITY");
        assertTrue(provider >= 0);
        assertTrue(earlier > provider);
        assertTrue(later > earlier);
        assertTrue(priorityBoundary > later);
        assertTrue(prompt.contains("[Source: test:earlier]"));
        assertTrue(prompt.contains("[Source: test:later]"));
    }

    @Test
    void duplicateContributorIdIsRejectedAndClosingHandleAllowsCleanReplacement() {
        registerProvider("test:provider", 100, provider("base"));
        AddonRegistration first = registerContributor("test:replaceable", 0,
                context -> List.of(PromptContribution.observation("test:replaceable", "One", "first")));

        assertThrows(IllegalArgumentException.class, () -> PromptRuntime.registerContributor(
                "test:replaceable", 1,
                context -> List.of(PromptContribution.observation("test:replaceable", "Two", "duplicate"))));

        first.close();
        registrations.remove(first);
        AddonRegistration replacement = registerContributor("test:replaceable", 0,
                context -> List.of(PromptContribution.observation("test:replaceable", "One", "replacement")));

        assertTrue(PromptRuntime.generateCitizenRoleplayPrompt(VIEW).contains("replacement"));
        assertFalse(replacement.isClosed());
    }

    @Test
    void brokenContributorDoesNotDisableOtherContributors() {
        registerProvider("test:provider", 100, provider("base"));
        registerContributor("test:broken", 0, context -> {
            throw new IllegalStateException("expected test failure");
        });
        registerContributor("test:healthy", 1, context -> List.of(
                PromptContribution.observation("test:healthy", "Healthy", "healthy-context")));

        String prompt = PromptRuntime.generateCitizenRoleplayPrompt(VIEW);
        assertTrue(prompt.startsWith("base:CITIZEN_ROLEPLAY"));
        assertTrue(prompt.contains("healthy-context"));
    }

    @Test
    void renderedAddonContextHasABoundedTotalBudget() {
        registerProvider("test:provider", 100, provider("base"));
        registerContributor("test:budget", 0, context -> List.of(
                PromptContribution.observation("test:budget", "A", "A".repeat(8_000)),
                PromptContribution.observation("test:budget", "B", "B".repeat(8_000)),
                PromptContribution.observation("test:budget", "C", "C".repeat(8_000)),
                PromptContribution.observation("test:budget", "D", "D".repeat(8_000))
        ));

        String prompt = PromptRuntime.generateCitizenRoleplayPrompt(VIEW);

        assertTrue(prompt.contains("A".repeat(100)));
        assertTrue(prompt.contains("B".repeat(100)));
        assertTrue(prompt.contains("C".repeat(100)));
        assertFalse(prompt.contains("D".repeat(100)));
        // 24k is the addon block budget. The base and fixed core-priority footer are deliberately outside it.
        assertTrue(prompt.length() < 25_000, "rendered prompt should remain tightly bounded");
    }

    @Test
    void everyPromptSurfaceInvokesContributorsExactlyOnce() {
        registerProvider("test:provider", 100, provider("base"));
        EnumMap<PromptTarget, Integer> calls = new EnumMap<>(PromptTarget.class);
        registerContributor("test:counter", 0, context -> {
            calls.merge(context.target(), 1, Integer::sum);
            return List.of(PromptContribution.observation("test:counter", "Target", context.target().name()));
        });

        PromptRuntime.generateCitizenRoleplayPrompt(VIEW);
        PromptRuntime.generateSystemControlledRoleplayPrompt(VIEW);
        PromptRuntime.generateConversationalInfoPrompt(VIEW);
        PromptRuntime.getBasicCitizenInfoPrompt(VIEW);
        PromptRuntime.getDetailedCitizenInfoPrompt(VIEW);

        for (PromptTarget target : PromptTarget.values()) {
            assertEquals(1, calls.getOrDefault(target, 0), "unexpected invocation count for " + target);
        }
    }

    @Test
    void sessionAgendaIsImmutablePerPromptAndDoesNotLeakToLaterSessions() {
        registerProvider("test:provider", 100, provider("base"));
        List<PromptSessionContext> seen = new ArrayList<>();
        registerContributor("test:agenda", 0, context -> {
            seen.add(context.session());
            String agenda = context.session().agenda();
            return agenda == null
                    ? List.of()
                    : List.of(PromptContribution.observation("test:agenda", "Agenda", agenda));
        });

        PromptSessionContext firstSession = PromptSessionContext.withAgenda("First agenda");

        String first = PromptRuntime.generateSystemControlledRoleplayPrompt(VIEW, firstSession);
        String second = PromptRuntime.generateSystemControlledRoleplayPrompt(VIEW, PromptSessionContext.empty());

        assertTrue(first.contains("First agenda"));
        assertFalse(second.contains("First agenda"));
        assertEquals(2, seen.size());
        assertSame(firstSession, seen.get(0));
        assertEquals("First agenda", seen.get(0).agenda());
        assertTrue(seen.get(1).isEmpty());
    }

    @Test
    void explicitSourceIsRenderedAndPromptLabelsCannotInjectExtraSections() {
        registerProvider("test:provider", 100, provider("base"));
        registerContributor("test:source", 0, context -> List.of(
                PromptContribution.observation("voyager:expedition_result", "Expedition", "returned safely")));

        String prompt = PromptRuntime.generateCitizenRoleplayPrompt(VIEW);
        assertTrue(prompt.contains("[Source: voyager:expedition_result]"));
        assertThrows(IllegalArgumentException.class,
                () -> PromptContribution.observation("bad\nsource", "Section", "text"));
        assertThrows(IllegalArgumentException.class,
                () -> PromptContribution.observation("source", "bad\nsection", "text"));
    }

    private AddonRegistration registerContributor(
            String id,
            int order,
            me.sshcrack.mc_talking.api.prompt.CitizenPromptContributor contributor
    ) {
        AddonRegistration registration = PromptRuntime.registerContributor(id, order, contributor);
        registrations.add(registration);
        return registration;
    }

    private void registerProvider(String id, int priority, CitizenPromptProvider provider) {
        registrations.add(PromptRuntime.registerProvider(id, priority, provider));
    }

    private static CitizenPromptProvider provider(String base) {
        return new CitizenPromptProvider() {
            @Override
            public String getBasicCitizenInfoPrompt(CitizenPromptView view, boolean firstPerson) {
                return base + ":BASIC_CITIZEN_INFO";
            }

            @Override
            public String generateCitizenRoleplayPrompt(CitizenPromptView view) {
                return base + ":CITIZEN_ROLEPLAY";
            }

            @Override
            public String getDetailedCitizenInfoPrompt(CitizenPromptView view) {
                return base + ":DETAILED_CITIZEN_INFO";
            }

            @Override
            public String generateConversationalInfoPrompt(CitizenPromptView view) {
                return base + ":CONVERSATIONAL_INFO";
            }

            @Override
            public String generateSystemControlledRoleplayPrompt(CitizenPromptView view) {
                return base + ":SYSTEM_CONTROLLED_ROLEPLAY";
            }
        };
    }
}
