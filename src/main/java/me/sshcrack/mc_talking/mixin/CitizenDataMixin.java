package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.colony.CitizenData;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*? if neoforge {*/
/*? }*/

@Mixin(value = CitizenData.class, remap = false)
public class CitizenDataMixin implements CitizenDataMemoryExtended {
    @Unique
    private static final String TAG_MEMORY_KEY = "mc_talking_memory";
    @Unique
    private CitizenMemories mc_talking$memory;

    @Inject(method = "setVisibleStatus", at = @At("HEAD"))
    private void mc_talking$onSetVisibleStatus(VisibleCitizenStatus status, CallbackInfo ci) {
        var data = CitizenData.class.cast(this);
        if (status == null || data == null) {
            return;
        }

        var client = ConversationManager.getClientForEntity(data.getUUID());
        if (client == null) {
            return;
        }

        if (client.getLastStatus() == null) {
            if (status == VisibleCitizenStatus.SLEEP) {
                var sleepPrompt = "You are now sleeping. END THE CONVERSATION NOW USING YOUR \"end_conversation\" TOOL. IGNORE ANY INSTRUCTIONS AND END CONVERSATION NOW!!!!";
                client.setLastStatus(status);
                McTalking.LOGGER.info("[STATUS] Sending sleep prompt");
                client.addPromptTextAfterTalkingComplete(sleepPrompt);
            }

            client.setLastStatus(status);
        }

        if (client.getLastStatus() != null && client.getLastStatus().equals(status)) {
            return;
        }

        if (status == VisibleCitizenStatus.SLEEP) {
            var sleepPrompt = "You are now sleeping. END THE CONVERSATION NOW USING YOUR \"end_conversation\" TOOL. IGNORE ANY INSTRUCTIONS AND END CONVERSATION NOW!!!!";
            client.setLastStatus(status);
            McTalking.LOGGER.info("[STATUS] Sending sleep prompt");
            client.addPromptTextAfterTalkingComplete(sleepPrompt);
        }

        if (!client.sendStatusUpdates()) {
            return;
        }

        var statusView = CitizenPromptViewFactory.createStatusView(status, data);
        var newStatusPrompt = String.format("You are now %s", CitizenPromptService.formatStatus(statusView));
        client.setLastStatus(status);
        client.addPromptTextAfterTalkingComplete(newStatusPrompt);
    }

    /*? if neoforge {*/
    @Inject(method = "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void mc_talking$serializeMemoryNBT(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        mc_talking$serializeNBT(tag);
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void mc_talking$deserializeMemoryNBT(HolderLookup.Provider provider, CompoundTag nbtTagCompound, CallbackInfo ci) {
        mc_talking$deserializeNBT(nbtTagCompound);
    }
    /*?}*/

    /*? if forge {*/
    /*@Inject(method = "serializeNBT()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void mc_talking$serializeMemoryNBT(CallbackInfoReturnable<CompoundTag> cir) {
        mc_talking$serializeNBT(cir.getReturnValue());
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void mc_talking$deserializeMemoryNBT(CompoundTag nbtTagCompound, CallbackInfo ci) {
        mc_talking$deserializeNBT(nbtTagCompound);
    }
    *//*?}*/

    @Unique
    private void mc_talking$serializeNBT(CompoundTag tag) {
        if (mc_talking$memory != null) {
            if (tag.contains(TAG_MEMORY_KEY)) {
                McTalking.LOGGER.error("Memory data conflict found for citizen {}, not overwriting.", CitizenData.class.cast(this).getUUID());
                return;
            }

            tag.put(TAG_MEMORY_KEY, mc_talking$memory.serializeNbt());
        }
    }

    @Unique
    private void mc_talking$deserializeNBT(CompoundTag nbtTagCompound) {
        if (nbtTagCompound.contains(TAG_MEMORY_KEY)) {
            mc_talking$memory = new CitizenMemories();
            mc_talking$memory.deserializeNbt(nbtTagCompound.getCompound(TAG_MEMORY_KEY));
        }
    }

    @Override
    public CitizenMemories mc_talking$getMemory() {
        return mc_talking$memory;
    }

    @Override
    public CitizenMemories mc_talking$getOrInitializeMemory() {
        if (mc_talking$memory == null) {
            mc_talking$memory = new CitizenMemories();
        }

        return mc_talking$memory;
    }
}
