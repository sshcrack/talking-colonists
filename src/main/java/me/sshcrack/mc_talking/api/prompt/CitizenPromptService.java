package me.sshcrack.mc_talking.api.prompt;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.manager.DefaultCitizenPromptProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global prompt provider registry used by McTalking.
 * Other mods can replace the default behavior by calling {@link #setProvider(CitizenPromptProvider)}
 * during setup.
 */
public final class CitizenPromptService {
    private static final CitizenPromptProvider DEFAULT_PROVIDER = new DefaultCitizenPromptProvider();
    private static final AtomicReference<CitizenPromptProvider> PROVIDER = new AtomicReference<>(DEFAULT_PROVIDER);

    private CitizenPromptService() {
    }

    public static void setProvider(@NotNull CitizenPromptProvider provider) {
        var previous = PROVIDER.getAndSet(Objects.requireNonNull(provider, "provider"));
        McTalking.LOGGER.info("Citizen prompt provider changed from {} to {}",
                previous.getClass().getName(),
                provider.getClass().getName());
    }

    public static void resetToDefault() {
        setProvider(DEFAULT_PROVIDER);
    }

    public static @NotNull CitizenPromptProvider getProvider() {
        return PROVIDER.get();
    }

    public static String generateCitizenRoleplayPrompt(@NotNull CitizenPromptView view) {
        return getProvider().generateCitizenRoleplayPrompt(view);
    }

    public static String generatePlayBackgroundNoisePrompt(@NotNull CitizenPromptView view) {
        return getProvider().generatePlayBackgroundNoisePrompt(view);
    }

    public static String generateConversationalInfoPrompt(@NotNull CitizenPromptView view) {
        return getProvider().generateConversationalInfoPrompt(view);
    }

    public static String getBasicCitizenInfoPrompt(@NotNull CitizenPromptView view) {
        return getProvider().getBasicCitizenInfoPrompt(view, false);
    }

    public static String getDetailedCitizenInfoPrompt(@NotNull CitizenPromptView view) {
        return getProvider().getDetailedCitizenInfoPrompt(view);
    }

    public static String formatStatus(CitizenStatusView status) {
        return getProvider().formatStatus(status);
    }

    public static String generateSystemControlledRoleplayPrompt(@NotNull CitizenPromptView view) {
        return getProvider().generateSystemControlledRoleplayPrompt(view);
    }
}
