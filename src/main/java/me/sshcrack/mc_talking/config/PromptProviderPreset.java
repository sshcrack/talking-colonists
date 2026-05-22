package me.sshcrack.mc_talking.config;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum PromptProviderPreset implements NameableEnum {
    DEFAULT,
    MEDIUM_MADNESS,
    LOW_MADNESS,
    FRIENDLY,
    STOIC,
    PRACTICAL;

    @Override
    public Component getDisplayName() {
        return Component.literal(name());
    }
}
