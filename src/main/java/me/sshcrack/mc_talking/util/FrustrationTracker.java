package me.sshcrack.mc_talking.util;

import me.sshcrack.mc_talking.api.prompt.view.*;
import net.minecraft.nbt.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FrustrationTracker {

    private final Map<String, Long> onsetTicks = new HashMap<>();

    private long cooldownRemainingTicks = 0;

    @Nullable
    private FrustrationData lastResult = null;

    public boolean isInCooldown() { return cooldownRemainingTicks > 0; }

    public void setCooldownTicks(long ticks) { cooldownRemainingTicks = ticks; }

    public void tickCooldown(long intervalTicks) {
        cooldownRemainingTicks = Math.max(0, cooldownRemainingTicks - intervalTicks);
    }

    public long getCooldownRemainingTicks() { return cooldownRemainingTicks; }

    public FrustrationData compute(
        List<HappinessModifierView> currentModifiers,
        long currentGameTimeTicks,
        double negativeThreshold,
        long[] tierThresholds,
        @Nullable ColonyContext colonyCtx
    ) {
        if (cooldownRemainingTicks > 0) {
            lastResult = FrustrationData.COOLDOWN;
            return lastResult;
        }

        Set<String> activeNow = new HashSet<>();
        for (var mod : currentModifiers) {
            if (mod.factor() < negativeThreshold) {
                activeNow.add(mod.type().name());
            }
        }
        onsetTicks.keySet().retainAll(activeNow);

        for (var mod : currentModifiers) {
            if (mod.factor() < negativeThreshold) {
                onsetTicks.putIfAbsent(mod.type().name(), currentGameTimeTicks);
            }
        }

        List<FrustrationModifierView> views = new ArrayList<>();
        FrustrationTier overall = FrustrationTier.NEUTRAL;

        for (var mod : currentModifiers) {
            long onset = onsetTicks.getOrDefault(mod.type().name(), -1L);
            if (onset < 0) continue;

            long raw = Math.max(0, currentGameTimeTicks - onset);
            long adjusted = colonyCtx != null
                ? colonyCtx.applyMitigation(mod.type(), raw)
                : raw;
            FrustrationTier tier = FrustrationTier.forDuration(adjusted, tierThresholds);

            String note = colonyCtx != null ? colonyCtx.getNotes(mod.type()) : null;
            views.add(new FrustrationModifierView(
                mod.type(), mod.factor(), raw, adjusted, tier, note));

            if (tier.getLevel() > overall.getLevel()) overall = tier;
        }

        lastResult = new FrustrationData(overall, Collections.unmodifiableList(views), false);
        return lastResult;
    }

    @Nullable
    public FrustrationData getLastResult() { return lastResult; }

    private static final String TAG_ONSET      = "onset";
    private static final String TAG_MOD_ID     = "id";
    private static final String TAG_MOD_TICK   = "tick";
    private static final String TAG_COOLDOWN   = "cooldown";

    public CompoundTag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_COOLDOWN, cooldownRemainingTicks);
        ListTag list = new ListTag();
        for (var entry : onsetTicks.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putString(TAG_MOD_ID, entry.getKey());
            e.putLong(TAG_MOD_TICK, entry.getValue());
            list.add(e);
        }
        tag.put(TAG_ONSET, list);
        return tag;
    }

    public void deserializeNbt(CompoundTag tag) {
        cooldownRemainingTicks = tag.getLong(TAG_COOLDOWN);
        onsetTicks.clear();
        ListTag list = tag.getList(TAG_ONSET, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            String id = e.getString(TAG_MOD_ID);
            long tick = e.getLong(TAG_MOD_TICK);
            try {
                HappinessModifierType.valueOf(id);
                onsetTicks.put(id, tick);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
