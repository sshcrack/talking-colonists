package me.sshcrack.mc_talking.client;

import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.client.Minecraft;

/** Opens the Talking Colonists config screen. Client-only: never load this class on a dedicated server. */
public final class ConfigScreenOpener {
    private ConfigScreenOpener() {
    }

    /**
     * Opens the screen on the next client tick, so the chat screen that ran the click
     * finishes closing first. Closing the config returns to the game.
     */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(McTalkingConfig.INSTANCE.generateGui().generateScreen(null)));
    }
}
