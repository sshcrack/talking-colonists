package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.colony.CitizenData;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.MusicPlaybackMode;
import me.sshcrack.mc_talking.config.PersonalityArchetype;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.duck.CitizenDataPersonalityExtended;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.manager.music.MusicManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;


@Mixin(value = CitizenData.class, remap = false)
public class CitizenDataMixin implements CitizenDataMemoryExtended, CitizenDataPersonalityExtended {
    @Unique
    private static final String TAG_MEMORY_KEY = "mc_talking_memory";
    @Unique
    private static final String TAG_PERSONALITY_KEY = "mc_talking_personality";
    @Unique
    private CitizenMemories mc_talking$memory;
    @Unique
    @Nullable
    private PersonalityArchetype mc_talking$personality;
    @Unique
    @Nullable
    private String mc_talking$customPersonality;

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

        mc_talking$maybePlayStatusMusic(status, data);
    }

    @Unique
    private void mc_talking$maybePlayStatusMusic(VisibleCitizenStatus status, CitizenData data) {
        var config = McTalkingConfig.INSTANCE.instance();
        if (config.musicPlaybackMode != MusicPlaybackMode.SERVER_SIDE) {
            return;
        }

        if (status == VisibleCitizenStatus.SLEEP
                || status == VisibleCitizenStatus.MOURNING
                || status == VisibleCitizenStatus.SICK
                || status == VisibleCitizenStatus.RAIDED) {
            return;
        }

        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty()) {
            return;
        }

        double chance = 0.08;
        if (status == VisibleCitizenStatus.WORKING) {
            chance = 0.35;
        } else if (status == VisibleCitizenStatus.HOUSE
                || status == VisibleCitizenStatus.EAT
                || status == VisibleCitizenStatus.BAD_WEATHER) {
            chance = 0.18;
        }

        if (Math.random() > chance) {
            return;
        }

        String query;
        if (status == VisibleCitizenStatus.WORKING) {
            String jobName = data.getJob() != null
                    ? Component.translatable(data.getJob().getJobRegistryEntry().getTranslationKey()).getString()
                    : "village work ambience";
            query = jobName + " instrumental background music";
        } else if (status == VisibleCitizenStatus.HOUSE) {
            query = "cozy village background music";
        } else if (status == VisibleCitizenStatus.EAT) {
            query = "warm tavern background music";
        } else if (status == VisibleCitizenStatus.BAD_WEATHER) {
            query = "gentle rainy background music";
        } else {
            query = "peaceful medieval background music";
        }

        try {
            MusicManager.getInstance().playQueryForEntity(entityOpt.get(), query, "status:" + status.toString().toLowerCase());
        } catch (Exception e) {
            McTalking.LOGGER.debug("Skipping background music trigger for {}", data.getUUID(), e);
        }
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
            } else {
                tag.put(TAG_MEMORY_KEY, mc_talking$memory.serializeNbt());
            }
        }
        // Personality
        if (mc_talking$personality != null) {
            tag.putString(TAG_PERSONALITY_KEY, mc_talking$personality.name());
        } else if (mc_talking$customPersonality != null) {
            tag.putString(TAG_PERSONALITY_KEY, "custom:" + mc_talking$customPersonality);
        }
    }

    @Unique
    private void mc_talking$deserializeNBT(CompoundTag nbtTagCompound) {
        if (nbtTagCompound.contains(TAG_MEMORY_KEY)) {
            mc_talking$memory = new CitizenMemories();
            mc_talking$memory.deserializeNbt(nbtTagCompound.getCompound(TAG_MEMORY_KEY));
        }
        if (nbtTagCompound.contains(TAG_PERSONALITY_KEY)) {
            String raw = nbtTagCompound.getString(TAG_PERSONALITY_KEY);
            if (raw.startsWith("custom:")) {
                mc_talking$customPersonality = raw.substring(7);
                mc_talking$personality = null;
            } else {
                try {
                    mc_talking$personality = PersonalityArchetype.valueOf(raw);
                    mc_talking$customPersonality = null;
                } catch (IllegalArgumentException ignored) {
                    // Unknown enum value (e.g. old save) — will be re-assigned lazily
                }
            }
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

    // -------------------------------------------------------------------------
    // CitizenDataPersonalityExtended
    // -------------------------------------------------------------------------

    @Override
    @Nullable
    public PersonalityArchetype mc_talking$getPersonality() {
        return mc_talking$personality;
    }

    @Override
    @Nullable
    public String mc_talking$getCustomPersonality() {
        return mc_talking$customPersonality;
    }

    @Override
    public void mc_talking$assignPersonality() {
        // Already assigned?
        if (mc_talking$personality != null || mc_talking$customPersonality != null) return;

        var config = McTalkingConfig.INSTANCE.instance();
        if (!config.enablePersonalityArchetypes) return;

        List<String> customs = config.customPersonalityArchetypes;
        int totalPool = PersonalityArchetype.values().length + customs.size();
        int pick = (int) (Math.random() * totalPool);

        if (pick < PersonalityArchetype.values().length) {
            mc_talking$personality = PersonalityArchetype.values()[pick];
        } else {
            mc_talking$customPersonality = customs.get(pick - PersonalityArchetype.values().length);
        }
    }
}
