package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.duck.CitizenDataFrustrationExtended;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public final class FrustrationHandler {

    private FrustrationHandler() {}

    public static void tick(MinecraftServer server) {
        var config = McTalkingConfig.INSTANCE.instance();
        if (!config.enableFrustration) return;

        long interval = config.frustrationCheckIntervalTicks;
        double negativeThreshold = config.frustrationNegativeThreshold;
        long[] tierThresholds = {
            config.mildlyAnnoyedThresholdTicks,
            config.concernedThresholdTicks,
            config.agitatedThresholdTicks,
            config.furiousThresholdTicks
        };

        for (var level : server.getAllLevels()) {
            IMinecoloniesAPI.getInstance().getColonyManager()
                .getColonies(level)
                .forEach(colony -> {
                    long gameTime = colony.getWorld() != null
                        ? colony.getWorld().getGameTime() : 0;
                    colony.getCitizenManager().getCitizens()
                        .forEach(data -> {
                            var tracker = ((CitizenDataFrustrationExtended) data)
                                .mc_talking$getFrustrationTracker();
                            tracker.tickCooldown(interval);

                            List<HappinessModifierView> modifiers = extractModifiers(data);
                            tracker.compute(modifiers, gameTime,
                                negativeThreshold, tierThresholds, null);
                        });
                });
        }
    }

    private static List<HappinessModifierView> extractModifiers(
            com.minecolonies.api.colony.ICitizenData data) {
        var handler = data.getCitizenHappinessHandler();
        List<HappinessModifierView> views = new ArrayList<>();
        for (String modifierId : handler.getModifiers()) {
            IHappinessModifier mod = handler.getModifier(modifierId);
            if (mod == null) continue;
            HappinessModifierType type = HappinessModifierType.fromId(modifierId);
            if (type != null) {
                views.add(new HappinessModifierView(type, mod.getFactor(data)));
            }
        }
        return views;
    }
}
