package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.prompt.CitizenPromptContributor;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.prompt.PromptContribution;
import me.sshcrack.mc_talking.api.prompt.PromptTarget;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Runtime-only prompt provider/contributor orchestration. */
public final class PromptRuntime {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");
    private static final int MAX_CONTRIBUTION_CHARS = 8_000;
    private static final int MAX_TOTAL_CONTRIBUTION_CHARS = 24_000;
    private static final RegistrationRegistry<CitizenPromptProvider> PROVIDERS =
            new RegistrationRegistry<>("Prompt provider");
    private static final RegistrationRegistry<CitizenPromptContributor> CONTRIBUTORS =
            new RegistrationRegistry<>("Prompt contributor");

    private PromptRuntime() {
    }

    public static @NotNull AddonRegistration registerProvider(
            @NotNull String id,
            int priority,
            @NotNull CitizenPromptProvider provider
    ) {
        AddonRegistration registration = PROVIDERS.register(id, priority, provider);
        LOGGER.log(System.Logger.Level.INFO, "Registered citizen prompt provider {0} (priority={1})", id, priority);
        return registration;
    }

    public static @NotNull CitizenPromptProvider getProvider() {
        var ordered = PROVIDERS.orderedSnapshot();
        return ordered.isEmpty()
                ? TalkingColonistsApiBackend.INSTANCE.defaultPromptProvider()
                : ordered.get(ordered.size() - 1).value();
    }

    public static @NotNull AddonRegistration registerContributor(
            @NotNull String id,
            int order,
            @NotNull CitizenPromptContributor contributor
    ) {
        AddonRegistration registration = CONTRIBUTORS.register(id, order, contributor);
        LOGGER.log(System.Logger.Level.INFO, "Registered citizen prompt contributor {0} (order={1})", id, order);
        return registration;
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

    public static String formatStatus(@NotNull me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView status) {
        return getProvider().formatStatus(status);
    }

    public static String generateSystemControlledRoleplayPrompt(@NotNull CitizenPromptView view) {
        return appendContributions(getProvider().generateSystemControlledRoleplayPrompt(view), view,
                PromptTarget.SYSTEM_CONTROLLED_ROLEPLAY);
    }

    private static String appendContributions(String base, CitizenPromptView view, PromptTarget target) {
        StringBuilder result = new StringBuilder(base == null ? "" : base);
        int total = 0;
        for (var registration : CONTRIBUTORS.orderedSnapshot()) {
            List<PromptContribution> contributions;
            try {
                contributions = registration.value().contribute(view, target);
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
                if (text.length() > remaining) text = text.substring(0, remaining);
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
}
