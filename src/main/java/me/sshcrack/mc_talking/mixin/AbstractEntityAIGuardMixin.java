package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard;
import me.sshcrack.mc_talking.pregen.PregenerationTaskService;
import me.sshcrack.mc_talking.util.CitizenHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractEntityAIGuard.class, remap = false)
public abstract class AbstractEntityAIGuardMixin {

    @Shadow
    public abstract boolean canHelp(BlockPos pos);

    @Inject(method = "startHelpCitizen", at = @At(value = "HEAD"))
    private void mc_talking$guardRespondToThreat(LivingEntity attacker, CallbackInfo ci) {
        if (!canHelp(attacker.blockPosition()))
            return;

        if (!mc_talking$shouldGuardRespondToThreat(attacker))
            return;


        AbstractEntityCitizen guard = ((AbstractAISkeletonAccessor) this).getWorker();
        PregenerationTaskService.playThreatNow(guard, attacker);
    }


    @Unique
    private static boolean mc_talking$shouldGuardRespondToThreat(LivingEntity threat) {
        if (!(threat instanceof Mob hostile)) {
            return false;
        }

        LivingEntity attackedTarget = hostile.getTarget();
        if (!(attackedTarget instanceof AbstractEntityCitizen attackedCitizen))
            return false;
        return !CitizenHelper.isCitizenGuard(attackedCitizen);
    }
}
