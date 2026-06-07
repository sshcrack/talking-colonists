package me.sshcrack.mc_talking.broadcast;

import net.minecraft.nbt.CompoundTag;

// equals/hashCode intentionally omitted — identity is id-based, managed via knownBroadcastIds (HashSet<String>) in CitizenMemories.
public class ColonyBroadcast {
    private static final String TAG_ID = "id";
    private static final String TAG_ORIGINATOR = "originator";
    private static final String TAG_MESSAGE = "message";
    private static final String TAG_CREATED_AT = "created_at";
    private static final String TAG_SENDER_PLAYER = "sender_player";

    private final String id;
    private final String originatorName;
    private final String message;
    private final long createdAtMs;
    private final String senderPlayerName;

    public ColonyBroadcast(String id, String originatorName, String message, long createdAtMs, String senderPlayerName) {
        this.id = id;
        this.originatorName = originatorName;
        this.message = message;
        this.createdAtMs = createdAtMs;
        this.senderPlayerName = senderPlayerName;
    }

    public String getId() {
        return id;
    }

    public String getOriginatorName() {
        return originatorName;
    }

    public String getMessage() {
        return message;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public String getSenderPlayerName() {
        return senderPlayerName;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ID, id);
        tag.putString(TAG_ORIGINATOR, originatorName);
        tag.putString(TAG_MESSAGE, message);
        tag.putLong(TAG_CREATED_AT, createdAtMs);
        tag.putString(TAG_SENDER_PLAYER, senderPlayerName);
        return tag;
    }

    public static ColonyBroadcast deserialize(CompoundTag tag) {
        return new ColonyBroadcast(
                tag.getString(TAG_ID),
                tag.getString(TAG_ORIGINATOR),
                tag.getString(TAG_MESSAGE),
                tag.getLong(TAG_CREATED_AT),
                tag.contains(TAG_SENDER_PLAYER) ? tag.getString(TAG_SENDER_PLAYER) : "Unknown Player"
        );
    }
}
