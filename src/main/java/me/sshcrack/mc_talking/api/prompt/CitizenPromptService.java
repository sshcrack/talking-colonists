package me.sshcrack.mc_talking.api.prompt;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.PromptProviderPreset;
import me.sshcrack.mc_talking.manager.DefaultCitizenPromptProvider;
import me.sshcrack.mc_talking.manager.PresetCitizenPromptProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global prompt provider registry used by McTalking.
 * Other mods can replace the default behavior by calling {@link #setProvider(CitizenPromptProvider)}
 * during setup.
 */
public final class CitizenPromptService {
    private static final CitizenPromptProvider DEFAULT_PROVIDER = new DefaultCitizenPromptProvider();
    private static final CitizenPromptProvider MEDIUM_MADNESS_PROVIDER = new PresetCitizenPromptProvider(PromptProviderPreset.MEDIUM_MADNESS);
    private static final CitizenPromptProvider LOW_MADNESS_PROVIDER = new PresetCitizenPromptProvider(PromptProviderPreset.LOW_MADNESS);
    private static final CitizenPromptProvider FRIENDLY_PROVIDER = new PresetCitizenPromptProvider(PromptProviderPreset.FRIENDLY);
    private static final CitizenPromptProvider STOIC_PROVIDER = new PresetCitizenPromptProvider(PromptProviderPreset.STOIC);
    private static final CitizenPromptProvider PRACTICAL_PROVIDER = new PresetCitizenPromptProvider(PromptProviderPreset.PRACTICAL);
    private static final AtomicReference<CitizenPromptProvider> PROVIDER_OVERRIDE = new AtomicReference<>(null);

    private CitizenPromptService() {
    }

    public static void setProvider(@NotNull CitizenPromptProvider provider) {
        setDefaultProvider(provider);
    }

    public static void setDefaultProvider(@NotNull CitizenPromptProvider provider) {
        var previous = PROVIDER_OVERRIDE.getAndSet(Objects.requireNonNull(provider, "provider"));
        McTalking.LOGGER.info("Citizen prompt provider changed from {} to {}",
                previous == null ? "<config>" : previous.getClass().getName(),
                provider.getClass().getName());
    }

    public static void resetToDefault() {
        PROVIDER_OVERRIDE.set(null);
    }

    private static @NotNull CitizenPromptProvider getProviderForPreset(@Nullable PromptProviderPreset preset) {
        if (preset == null) {
            return DEFAULT_PROVIDER;
        }

        return switch (preset) {
            case DEFAULT -> DEFAULT_PROVIDER;
            case MEDIUM_MADNESS -> MEDIUM_MADNESS_PROVIDER;
            case LOW_MADNESS -> LOW_MADNESS_PROVIDER;
            case FRIENDLY -> FRIENDLY_PROVIDER;
            case STOIC -> STOIC_PROVIDER;
            case PRACTICAL -> PRACTICAL_PROVIDER;
        };
    }

    public static @NotNull CitizenPromptProvider getProvider() {
        var override = PROVIDER_OVERRIDE.get();
        if (override != null) {
            return override;
        }

        return getProviderForPreset(McTalkingConfig.INSTANCE.instance().promptProviderPreset);
    }

    public static String generateCitizenRoleplayPrompt(@NotNull CitizenPromptView view) {
        return getProvider().generateCitizenRoleplayPrompt(view);
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
