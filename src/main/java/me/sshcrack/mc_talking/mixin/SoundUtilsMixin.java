package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.util.SoundUtils;
import me.sshcrack.mc_talking.ConversationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundUtils.class)
public class SoundUtilsMixin {
    @Inject(method = "playSoundAtCitizenWith(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lcom/minecolonies/api/sounds/EventType;Lcom/minecolonies/api/colony/ICivilianData;DD)V", at = @At("HEAD"), cancellable = true)
    private static void mc_talking$preventSoundWhenTalking(Level worldIn, BlockPos position, EventType type, ICivilianData citizenData, double chance, double volume, CallbackInfo ci) {
        var c = ConversationManager.getClientForEntity(citizenData.getUUID());
        if(c == null)
            return;

        ci.cancel();
    }
}
