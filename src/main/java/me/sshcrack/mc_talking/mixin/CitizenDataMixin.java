package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.colony.CitizenData;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenDataMemoryExtended;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CitizenData.class, remap = false)
public class CitizenDataMixin implements CitizenDataMemoryExtended {
    private static final String TAG_MEMORY_KEY = "mc_talking_memory";
    @Unique
    private CitizenMemories memory;

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

        var statusView = CitizenPromptViewFactory.createStatusView(status, data);
        var newStatusPrompt = String.format("You are now %s", CitizenPromptService.formatStatus(statusView));
        client.promptSystemText(newStatusPrompt);
    }

    @Inject(method = "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void mc_talking$serializeMemoryNBT(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (memory != null) {
            if (tag.contains(TAG_MEMORY_KEY)) {
                McTalking.LOGGER.error("Memory data conflict found for citizen {}, not overwriting.", CitizenData.class.cast(this).getUUID());
                return;
            }

            tag.put(TAG_MEMORY_KEY, memory.serializeNbt(provider));
        }
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void mc_talking$deserializeMemoryNBT(HolderLookup.Provider provider, CompoundTag nbtTagCompound, CallbackInfo ci) {
        if (nbtTagCompound.contains(TAG_MEMORY_KEY)) {
            memory = new CitizenMemories();
            memory.deserializeNbt(nbtTagCompound.getCompound(TAG_MEMORY_KEY));
        }
    }

    @Override
    public CitizenMemories mc_talking$getMemory() {
        return memory;
    }

    @Override
    public CitizenMemories mc_talking$getOrInitializeMemory() {
        if (memory == null) {
            memory = new CitizenMemories();
        }

        return memory;
    }
}
