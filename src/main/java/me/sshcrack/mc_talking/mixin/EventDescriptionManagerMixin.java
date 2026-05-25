package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.colonyEvents.descriptions.IBuildingEventDescription;
import com.minecolonies.api.colony.colonyEvents.descriptions.IColonyEventDescription;
import com.minecolonies.core.colony.managers.EventDescriptionManager;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.util.ColonyMoodEventTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EventDescriptionManager.class, remap = false)
public class EventDescriptionManagerMixin {
    @Shadow
    @Final
    private IColony colony;

    @Inject(method = "addEventDescription", at = @At("RETURN"))
    private void mc_talking$onEvent(IColonyEventDescription event, CallbackInfo ci) {
        if (event == null || colony == null) {
            return;
        }
        int durationDays = McTalkingConfig.INSTANCE.instance().positiveEventMoodDurationDays;
        if (durationDays <= 0) {
            return;
        }

        String line = buildPositiveMoodLine(event);
        if (line == null) {
            return;
        }

        int expiresAt = colony.getDay() + durationDays;
        ColonyMoodEventTracker.recordPositiveEvent(colony.getID(), line, expiresAt);
    }

    private static String buildPositiveMoodLine(IColonyEventDescription event) {
        String eventType = event.getEventTypeId().toString().toLowerCase();
        String eventName = safeLower(event.getName());
        String display = safeLower(event.toDisplayString());
        String combined = eventType + " " + eventName + " " + display;

        if (!looksPositive(combined)) {
            return null;
        }

        if (event instanceof IBuildingEventDescription buildingEvent) {
            String buildingName = buildingEvent.getBuildingName();
            int level = buildingEvent.getLevel();
            if (buildingName != null && !buildingName.isBlank() && level > 0) {
                return "A colony building milestone happened today: " + buildingName + " reached level " + level + ".";
            }
            if (buildingName != null && !buildingName.isBlank()) {
                return "A colony building milestone happened today: " + buildingName + ".";
            }
            return "The colony just celebrated a major building milestone.";
        }

        if (combined.contains("raid")) {
            return "The colony just overcame a raid and everyone feels a little more resilient.";
        }

        return "The colony just had a positive development that people are still talking about.";
    }

    private static boolean looksPositive(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        boolean hasPositiveKeyword = text.contains("complete")
                || text.contains("completed")
                || text.contains("finish")
                || text.contains("finished")
                || text.contains("success")
                || text.contains("upgrade")
                || text.contains("raid")
                || text.contains("victory")
                || text.contains("defeat");

        boolean hasNegativeKeyword = text.contains("fail")
                || text.contains("failed")
                || text.contains("death")
                || text.contains("died")
                || text.contains("dead")
                || text.contains("lost")
                || text.contains("injur")
                || text.contains("destroy")
                || text.contains("burn");

        return hasPositiveKeyword && !hasNegativeKeyword;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
