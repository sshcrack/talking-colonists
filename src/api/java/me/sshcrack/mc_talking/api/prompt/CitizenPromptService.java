package me.sshcrack.mc_talking.api.prompt;

import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Global prompt service used by Talking Colonists.
 *
 * <p>Legacy integrations may still replace the complete provider with {@link #setProvider}. Addons
 * that only need to contribute context should prefer {@link #registerContributor}; multiple
 * contributors coexist deterministically and do not replace core prompt behavior.</p>
 */
public final class CitizenPromptService {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");
    private static final AtomicReference<CitizenPromptProvider> PROVIDER = new AtomicReference<>();
    private static final Pattern CONTRIBUTOR_ID = Pattern.compile("[a-z][a-z0-9_]{0,31}:[a-z][a-z0-9_]{0,31}");
    private static final int MAX_CONTRIBUTION_CHARS = 8_000;
    private static final int MAX_TOTAL_CONTRIBUTION_CHARS = 24_000;
    private static final Map<String, ContributorRegistration> CONTRIBUTORS = new LinkedHashMap<>();

    private CitizenPromptService() {
    }

    public static void setProvider(@NotNull CitizenPromptProvider provider) {
        CitizenPromptProvider replacement = Objects.requireNonNull(provider, "provider");
        CitizenPromptProvider previous = PROVIDER.getAndSet(replacement);
        LOGGER.log(System.Logger.Level.INFO, "Citizen prompt provider changed from {0} to {1}",
                previous == null ? "core default" : previous.getClass().getName(),
                replacement.getClass().getName());
    }

    public static void resetToDefault() {
        PROVIDER.set(null);
        LOGGER.log(System.Logger.Level.INFO, "Citizen prompt provider reset to core default");
    }

    public static @NotNull CitizenPromptProvider getProvider() {
        CitizenPromptProvider provider = PROVIDER.get();
        return provider != null ? provider : TalkingColonistsApi.backend().defaultPromptProvider();
    }

    /**
     * Registers composable addon context without replacing the active prompt provider.
     *
     * <p>Contributors run in ascending {@code order}, then lexicographic namespaced ID order. A
     * failing contributor is logged and skipped so it cannot disable other addons or core prompt
     * generation.</p>
     */
    public static synchronized @NotNull Registration registerContributor(
            @NotNull String id,
            int order,
            @NotNull CitizenPromptContributor contributor
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(contributor, "contributor");
        if (!CONTRIBUTOR_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Contributor id must be namespaced and match "
                    + CONTRIBUTOR_ID.pattern() + ": " + id);
        }
        if (CONTRIBUTORS.containsKey(id)) {
            throw new IllegalArgumentException("Prompt contributor already registered: " + id);
        }
        ContributorRegistration registration = new ContributorRegistration(id, order, contributor);
        CONTRIBUTORS.put(id, registration);
        LOGGER.log(System.Logger.Level.INFO, "Registered citizen prompt contributor {0} (order={1})", id, order);
        return new Registration(registration);
    }

    public static String generateCitizenRoleplayPrompt(@NotNull CitizenPromptView view) {
        return appendContributions(getProvider().generateCitizenRoleplayPrompt(view), view, PromptTarget.CITIZEN_ROLEPLAY);
    }

    public static String generateConversationalInfoPrompt(@NotNull CitizenPromptView view) {
        return appendContributions(getProvider().generateConversationalInfoPrompt(view), view, PromptTarget.CONVERSATIONAL_INFO);
    }

    public static String getBasicCitizenInfoPrompt(@NotNull CitizenPromptView view) {
        return appendContributions(getProvider().getBasicCitizenInfoPrompt(view, false), view, PromptTarget.BASIC_CITIZEN_INFO);
    }

    public static String getDetailedCitizenInfoPrompt(@NotNull CitizenPromptView view) {
        return appendContributions(getProvider().getDetailedCitizenInfoPrompt(view), view, PromptTarget.DETAILED_CITIZEN_INFO);
    }

    public static String formatStatus(CitizenStatusView status) {
        return getProvider().formatStatus(status);
    }

    public static String generateSystemControlledRoleplayPrompt(@NotNull CitizenPromptView view) {
        return appendContributions(getProvider().generateSystemControlledRoleplayPrompt(view), view,
                PromptTarget.SYSTEM_CONTROLLED_ROLEPLAY);
    }

    private static String appendContributions(String base, CitizenPromptView view, PromptTarget target) {
        StringBuilder result = new StringBuilder(base == null ? "" : base);
        int total = 0;
        for (ContributorRegistration registration : contributorSnapshot()) {
            List<PromptContribution> contributions;
            try {
                contributions = registration.contributor().contribute(view, target);
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Prompt contributor " + registration.id() + " failed for " + target + " and was skipped", t);
                continue;
            }
            if (contributions == null) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Prompt contributor {0} returned null for {1}; treating it as empty", registration.id(), target);
                continue;
            }
            for (PromptContribution contribution : contributions) {
                if (contribution == null) continue;
                String text = contribution.text();
                if (text.length() > MAX_CONTRIBUTION_CHARS) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Prompt contribution {0} / {1} exceeded {2} chars and was truncated",
                            registration.id(), contribution.section(), MAX_CONTRIBUTION_CHARS);
                    text = text.substring(0, MAX_CONTRIBUTION_CHARS);
                }
                int remaining = MAX_TOTAL_CONTRIBUTION_CHARS - total;
                if (remaining <= 0) {
                    LOGGER.log(System.Logger.Level.WARNING, "Addon prompt contribution budget exhausted for {0}", target);
                    return result.toString();
                }
                if (text.length() > remaining) {
                    text = text.substring(0, remaining);
                }
                if (text.isBlank()) continue;

                result.append("\n\n## ADDON CONTEXT — ").append(contribution.section()).append("\n");
                result.append(switch (contribution.kind()) {
                    case OBSERVATION -> "[Current observation] ";
                    case RECOLLECTION -> "[Recollection; current observations take precedence] ";
                    case INSTRUCTION -> "[Addon guidance] ";
                });
                result.append(text);
                total += text.length();
            }
        }
        return result.toString();
    }

    private static synchronized List<ContributorRegistration> contributorSnapshot() {
        List<ContributorRegistration> registrations = new ArrayList<>(CONTRIBUTORS.values());
        registrations.sort(Comparator.comparingInt(ContributorRegistration::order)
                .thenComparing(ContributorRegistration::id));
        return List.copyOf(registrations);
    }

    private record ContributorRegistration(String id, int order, CitizenPromptContributor contributor) {
    }

    /** Registration lifetime handle. Safe to close more than once. */
    public static final class Registration implements AutoCloseable {
        private final ContributorRegistration registration;
        private boolean closed;

        private Registration(ContributorRegistration registration) {
            this.registration = registration;
        }

        public String id() {
            return registration.id();
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            synchronized (CitizenPromptService.class) {
                if (CONTRIBUTORS.get(registration.id()) == registration) {
                    CONTRIBUTORS.remove(registration.id());
                    LOGGER.log(System.Logger.Level.INFO, "Unregistered citizen prompt contributor {0}", registration.id());
                }
            }
        }
    }
}
