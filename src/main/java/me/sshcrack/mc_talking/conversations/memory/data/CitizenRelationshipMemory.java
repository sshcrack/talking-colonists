package me.sshcrack.mc_talking.conversations.memory.data;

import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class CitizenRelationshipMemory {
    private static final String TAG_CHANGE = "change";
    private static final String TAG_TYPE = "type";
    private static final String TAG_TARGET_UUID = "targetUUID";
    private final UUID targetUUID;
    private final CitizenRelationshipDimension type;
    private float change;

    public CitizenRelationshipMemory(UUID targetUUID, CitizenRelationshipDimension type, float change) {
        this.targetUUID = targetUUID;
        this.type = type;
        this.change = change;
    }

    public CitizenRelationshipMemory(CompoundTag tag) {
        this.targetUUID = UUID.fromString(tag.getString(TAG_TARGET_UUID));
        this.type = CitizenRelationshipDimension.valueOf(tag.getString(TAG_TYPE));
        this.change = tag.getFloat(TAG_CHANGE);
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public CitizenRelationshipDimension getType() {
        return type;
    }

    public float getFactor() {
        return change;
    }

    public void addChange(float change) {
        this.change += change;
    }

    public CompoundTag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_TARGET_UUID, targetUUID.toString());
        tag.putString(TAG_TYPE, type.name());
        tag.putFloat(TAG_CHANGE, change);

        return tag;
    }
}
