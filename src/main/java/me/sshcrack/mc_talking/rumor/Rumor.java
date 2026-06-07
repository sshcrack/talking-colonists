package me.sshcrack.mc_talking.rumor;

import net.minecraft.nbt.CompoundTag;

public class Rumor {
    private static final String TAG_ID         = "id";
    private static final String TAG_ORIGINATOR = "originator";
    private static final String TAG_CONTENT    = "content";

    private final String id;
    private final String originatorName;
    private final String content;

    public Rumor(String id, String originatorName, String content) {
        this.id            = id;
        this.originatorName = originatorName;
        this.content       = content;
    }

    public String getId()            { return id; }
    public String getOriginatorName(){ return originatorName; }
    public String getContent()       { return content; }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ID,         id);
        tag.putString(TAG_ORIGINATOR, originatorName);
        tag.putString(TAG_CONTENT,    content);
        return tag;
    }

    public static Rumor deserialize(CompoundTag tag) {
        return new Rumor(
            tag.getString(TAG_ID),
            tag.getString(TAG_ORIGINATOR),
            tag.getString(TAG_CONTENT)
        );
    }
}
