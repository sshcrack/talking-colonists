package me.sshcrack.mc_talking.mixin;

import com.minecolonies.core.colony.Colony;
import me.sshcrack.mc_talking.duck.ColonyEventDataProvider;
import me.sshcrack.mc_talking.handler.ConstructionEventTracker;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ConcurrentLinkedDeque;

@Mixin(value = Colony.class, remap = false)
public class ColonyDataMixin implements ColonyEventDataProvider {
    @Unique
    private static final String TAG_DATA_ROOT_KEY = "mc_talking_events";
    @Unique
    private static final String TAG_EVENTS_LIST = "events";
    @Unique
    private static final String TAG_RAID_END = "raidEnd";
    @Unique
    private static final String TAG_RAID_LOST = "raidLost";
    @Unique
    private static final String TAG_CONSTRUCTION_EVENTS = "mc_talking_construction_events";

    @Unique
    private final ConcurrentLinkedDeque<ColonyEventBuffer.ColonyEvent> mc_talking$events = new ConcurrentLinkedDeque<>();
    @Unique
    private volatile long mc_talking$lastRaidEndTime = Long.MAX_VALUE;
    @Unique
    private volatile int mc_talking$lastRaidLostCitizens = 0;

    @Override
    public ConcurrentLinkedDeque<ColonyEventBuffer.ColonyEvent> mc_talking$getOrCreateEvents() {
        return mc_talking$events;
    }

    @Override
    public long mc_talking$getLastRaidEndTime() {
        return mc_talking$lastRaidEndTime;
    }

    @Override
    public void mc_talking$setLastRaidEndTime(long time) {
        mc_talking$lastRaidEndTime = time;
    }

    @Override
    public int mc_talking$getLastRaidLostCitizens() {
        return mc_talking$lastRaidLostCitizens;
    }

    @Override
    public void mc_talking$setLastRaidLostCitizens(int count) {
        mc_talking$lastRaidLostCitizens = count;
    }

    /*? if neoforge {*/
    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void mc_talking$serializeNBT(CompoundTag compound, HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        mc_talking$writeNBT(tag);
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("RETURN"))
    private void mc_talking$deserializeNBT(CompoundTag compound, HolderLookup.Provider provider, CallbackInfo ci) {
        mc_talking$readNBT(compound);
    }
    /*?}*/

    /*? if forge {*/
    /*@Inject(method = "write", at = @At("RETURN"))
    private void mc_talking$serializeNBT(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        mc_talking$writeNBT(tag);
    }

    @Inject(method = "read", at = @At("RETURN"))
    private void mc_talking$deserializeNBT(CompoundTag compound, CallbackInfo ci) {
        mc_talking$readNBT(compound);
    }
    *//*?}*/

    @Unique
    private void mc_talking$writeNBT(CompoundTag tag) {
        CompoundTag eventTag = new CompoundTag();

        ListTag eventsList = new ListTag();
        for (ColonyEventBuffer.ColonyEvent evt : mc_talking$events) {
            eventsList.add(evt.serialize());
        }
        eventTag.put(TAG_EVENTS_LIST, eventsList);

        if (mc_talking$lastRaidEndTime != Long.MAX_VALUE) {
            eventTag.putLong(TAG_RAID_END, mc_talking$lastRaidEndTime);
        }
        if (mc_talking$lastRaidLostCitizens > 0) {
            eventTag.putInt(TAG_RAID_LOST, mc_talking$lastRaidLostCitizens);
        }

        tag.put(TAG_DATA_ROOT_KEY, eventTag);

        int colonyId = ((com.minecolonies.api.colony.IColony)(Object)this).getID();
        tag.put(TAG_CONSTRUCTION_EVENTS,
            ConstructionEventTracker.serializeColony(colonyId));
    }

    @Unique
    private void mc_talking$readNBT(CompoundTag compound) {
        if (!compound.contains(TAG_DATA_ROOT_KEY)) return;

        CompoundTag eventTag = compound.getCompound(TAG_DATA_ROOT_KEY);

        mc_talking$events.clear();
        ListTag eventsList = eventTag.getList(TAG_EVENTS_LIST, Tag.TAG_COMPOUND);
        for (int i = eventsList.size() - 1; i >= 0; i--) {
            var event = ColonyEventBuffer.ColonyEvent.deserialize(eventsList.getCompound(i));
            if (event != null) {
                mc_talking$events.addFirst(event);
            }
        }
        ColonyEventBuffer.trimEvents(mc_talking$events);

        if (eventTag.contains(TAG_RAID_END)) {
            mc_talking$lastRaidEndTime = eventTag.getLong(TAG_RAID_END);
        }
        if (eventTag.contains(TAG_RAID_LOST)) {
            mc_talking$lastRaidLostCitizens = eventTag.getInt(TAG_RAID_LOST);
        }

        if (compound.contains(TAG_CONSTRUCTION_EVENTS)) {
            int colonyId = ((com.minecolonies.api.colony.IColony)(Object)this).getID();
            ConstructionEventTracker.deserializeColony(colonyId,
                compound.getList(TAG_CONSTRUCTION_EVENTS, Tag.TAG_COMPOUND));
        }
    }
}
