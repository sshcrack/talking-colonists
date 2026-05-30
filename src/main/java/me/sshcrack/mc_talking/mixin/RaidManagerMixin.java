package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.colony.colonyEvents.IColonyRaidEvent;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.events.raid.RaidManager;
import me.sshcrack.mc_talking.util.RaidTraumaTracker;
import net.minecraft.world.Difficulty;
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
        var world = colony.getWorld();
        if (world != null && world.getDifficulty() == Difficulty.PEACEFUL) return;
        int lostCitizens = ((RaidManager) (Object) this).getLostCitizen();
        RaidTraumaTracker.recordRaid(colony.getID(), lostCitizens);
    }
}
