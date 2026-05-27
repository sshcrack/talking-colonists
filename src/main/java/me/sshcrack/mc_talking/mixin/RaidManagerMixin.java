package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.colony.colonyEvents.IColonyRaidEvent;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.events.raid.RaidManager;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.util.ColonyMoodEventTracker;
import me.sshcrack.mc_talking.util.RaidTraumaTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into {@code RaidManager.onRaidEventFinished} — which fires only when
 * the very last active raid of a wave ends — and records the event in
 * {@link RaidTraumaTracker} so citizen prompts can express post-raid trauma.
 */
@Mixin(value = RaidManager.class, remap = false)
public class RaidManagerMixin {

    @Shadow
    @Final
    private Colony colony;

    @Inject(method = "onRaidEventFinished", at = @At("RETURN"))
    private void mc_talking$onRaidFinished(IColonyRaidEvent finishedRaid, CallbackInfo ci) {
        if (colony == null) return;
        int lostCitizens = ((RaidManager) (Object) this).getLostCitizen();
        RaidTraumaTracker.recordRaid(colony.getID(), lostCitizens);
        
        int durationDays = McTalkingConfig.INSTANCE.instance().positiveEventMoodDurationDays;
        if (durationDays <= 0) {
            return;
        }

        String raidDescription = buildRaidEventDescription(finishedRaid, lostCitizens);
        
        // Record raids as negative events since they're inherently stressful/traumatic,
        // even if successfully defended against
        ColonyMoodEventTracker.recordNegativeEvent(colony.getID(), raidDescription, colony.getDay() + durationDays);
    }

    private static String buildRaidEventDescription(IColonyRaidEvent raidEvent, int lostCitizens) {
        if (lostCitizens > 0) {
            return "Raid Event: " + lostCitizens + " " + (lostCitizens == 1 ? "citizen" : "citizens") + " lost in a raid. The colony defended but at great cost.";
        } else {
            return "Raid Event: The colony successfully defended against raiders without losing anyone, but the trauma remains.";
        }
    }
}
