package me.sshcrack.mc_talking.config;

import java.util.Locale;
import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

public enum MemoryMode implements NameableEnum {
    LIVE,
    FLASH;

    @Override
    public Component getDisplayName() {
        return Component.translatable("mc_talking.memory_mode." + name().toLowerCase(Locale.ROOT));
    }
}
