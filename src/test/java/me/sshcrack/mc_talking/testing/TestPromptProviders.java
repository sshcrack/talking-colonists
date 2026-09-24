package me.sshcrack.mc_talking.testing;

import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import me.sshcrack.mc_talking.manager.DefaultCitizenPromptProvider;

/** Installs the real default prompt provider with the shipped config defaults. */
public final class TestPromptProviders {
    /** Mirrors the {@code McTalkingConfig} defaults, which cannot load outside a running game. */
    public static final DefaultCitizenPromptProvider.PromptLimits DEFAULT_LIMITS =
            new DefaultCitizenPromptProvider.PromptLimits(3, 3, 1200);

    private TestPromptProviders() {
    }

    public static void installDefault() {
        PromptRuntime.installDefaultProvider(new DefaultCitizenPromptProvider(() -> DEFAULT_LIMITS));
    }
}
