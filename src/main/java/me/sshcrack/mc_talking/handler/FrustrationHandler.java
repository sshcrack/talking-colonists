package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.IMinecoloniesAPI;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.duck.CitizenDataFrustrationExtended;
import net.minecraft.server.MinecraftServer;

public final class FrustrationHandler {

    private FrustrationHandler() {}

    public static void tick(MinecraftServer server) {
        var config = McTalkingConfig.INSTANCE.instance();
        if (!config.enableFrustration) return;

        long interval = config.frustrationCheckIntervalTicks;

        for (var level : server.getAllLevels()) {
            IMinecoloniesAPI.getInstance().getColonyManager()
                .getColonies(level)
                .forEach(colony ->
                    colony.getCitizenManager().getCitizens()
                        .forEach(data -> {
                            var tracker = ((CitizenDataFrustrationExtended) data)
                                .mc_talking$getFrustrationTracker();
                            tracker.tickCooldown(interval);
                        })
                );
        }
    }
}
