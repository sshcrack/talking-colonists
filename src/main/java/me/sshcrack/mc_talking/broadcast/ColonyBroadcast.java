package me.sshcrack.mc_talking.broadcast;

import me.sshcrack.mc_talking.api.memory.MemoryProvenance;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

// equals/hashCode intentionally omitted — identity is id-based, managed via knownBroadcastIds (HashSet<String>) in CitizenMemories.
public class ColonyBroadcast {
    private static final String TAG_ID = "id";
    private static final String TAG_ORIGINATOR = "originator";
    private static final String TAG_MESSAGE = "message";
    private static final String TAG_CREATED_AT = "created_at";
    private static final String TAG_SENDER_PLAYER = "sender_player";
    private static final String TAG_PROVENANCE = "provenance";
    private static final String TAG_SOURCE_LABEL = "source_label";
    private static final String TAG_EXPIRES_AT = "expires_at";
    private static final String TAG_ANNOUNCE = "announce";

    private final String id;
    private final String originatorName;
    private final String message;
    private final long createdAtMs;
    private final String senderPlayerName;
    private final MemoryProvenance provenance;
    /** Non-player source such as "the notice board"; null for player broadcasts. */
    private final @Nullable String sourceLabel;
    /** Epoch millis after which citizens forget the broadcast; 0 = never. */
    private final long expiresAtMs;
    private final boolean announceAloud;

    public ColonyBroadcast(String id, String originatorName, String message, long createdAtMs, String senderPlayerName) {
        this(id, originatorName, message, createdAtMs, senderPlayerName, MemoryProvenance.PLAYER_STATEMENT, null, 0L, true);
    }

    public ColonyBroadcast(String id, String originatorName, String message, long createdAtMs, String senderPlayerName,
                           MemoryProvenance provenance, @Nullable String sourceLabel, long expiresAtMs,
                           boolean announceAloud) {
        this.id = id;
        this.originatorName = originatorName;
        this.message = message;
        this.createdAtMs = createdAtMs;
        this.senderPlayerName = senderPlayerName;
        this.provenance = provenance;
        this.sourceLabel = sourceLabel;
        this.expiresAtMs = expiresAtMs;
        this.announceAloud = announceAloud;
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

    public MemoryProvenance getProvenance() {
        return provenance;
    }

    public @Nullable String getSourceLabel() {
        return sourceLabel;
    }

    public long getExpiresAtMs() {
        return expiresAtMs;
    }

    public boolean isAnnounceAloud() {
        return announceAloud;
    }

    public boolean isExpired(long nowMs) {
        return expiresAtMs > 0 && nowMs >= expiresAtMs;
    }

    /** Who citizens say the broadcast comes from when announcing it. */
    public String describeSource() {
        return sourceLabel != null ? sourceLabel : senderPlayerName;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ID, id);
        tag.putString(TAG_ORIGINATOR, originatorName);
        tag.putString(TAG_MESSAGE, message);
        tag.putLong(TAG_CREATED_AT, createdAtMs);
        tag.putString(TAG_SENDER_PLAYER, senderPlayerName);
        tag.putString(TAG_PROVENANCE, provenance.name());
        if (sourceLabel != null) tag.putString(TAG_SOURCE_LABEL, sourceLabel);
        if (expiresAtMs > 0) tag.putLong(TAG_EXPIRES_AT, expiresAtMs);
        tag.putBoolean(TAG_ANNOUNCE, announceAloud);
        return tag;
    }

    public static ColonyBroadcast deserialize(CompoundTag tag) {
        MemoryProvenance provenance = MemoryProvenance.PLAYER_STATEMENT;
        if (tag.contains(TAG_PROVENANCE)) {
            try {
                provenance = MemoryProvenance.valueOf(tag.getString(TAG_PROVENANCE));
            } catch (IllegalArgumentException ignored) {
                // Unknown value from a newer version: keep the player default.
            }
        }
        return new ColonyBroadcast(
                tag.getString(TAG_ID),
                tag.getString(TAG_ORIGINATOR),
                tag.getString(TAG_MESSAGE),
                tag.getLong(TAG_CREATED_AT),
                tag.contains(TAG_SENDER_PLAYER) ? tag.getString(TAG_SENDER_PLAYER) : "Unknown Player",
                provenance,
                tag.contains(TAG_SOURCE_LABEL) ? tag.getString(TAG_SOURCE_LABEL) : null,
                tag.getLong(TAG_EXPIRES_AT),
                !tag.contains(TAG_ANNOUNCE) || tag.getBoolean(TAG_ANNOUNCE)
        );
    }
}
