package me.sshcrack.mc_talking.conversations.memory.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.UUID;

public class CitizenRelationshipMemory {
    private static final String TAG_CHANGE = "change";
    private static final String TAG_TYPE = "type";
    private static final String TAG_OTHER_CITIZEN_ID = "otherCitizenId";
    private final UUID otherCitizenId;
    private final CitizenRelationshipChangeType type;
    private float change;

    public CitizenRelationshipMemory(UUID otherCitizenId, CitizenRelationshipChangeType type, float change) {
        this.otherCitizenId = otherCitizenId;
        this.type = type;
        this.change = change;
    }

    public CitizenRelationshipMemory(CompoundTag tag) {
        this.otherCitizenId = UUID.fromString(tag.getString(TAG_OTHER_CITIZEN_ID));
        this.type = CitizenRelationshipChangeType.valueOf(tag.getString(TAG_TYPE));
        this.change = tag.getFloat(TAG_CHANGE);
    }

    public UUID getOtherCitizenId() {
        return otherCitizenId;
    }

    public CitizenRelationshipChangeType getType() {
        return type;
    }

    public float getChange() {
        return change;
    }

    public void addChange(float change) {
        this.change += change;
    }

    public CompoundTag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_OTHER_CITIZEN_ID, otherCitizenId.toString());
        tag.putString(TAG_TYPE, type.name());
        tag.putFloat(TAG_CHANGE, change);

        return tag;
    }
}
