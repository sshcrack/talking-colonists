package me.sshcrack.mc_talking.network;

/*? if neoforge {*/
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
/*?}*/

// Don't forget to also update `en_us.json`!!
public enum AiStatus {
    ERROR,
    THINKING,
    QUOTA_EXCEEDED,
    TALKING,
    IN_CONVERSATION,
    LISTENING,
    NONE,
    RECONNECTING,
    URGENT_WALKING;

    public static AiStatus fromId(int id) {
        if (id < 0 || id >= values().length) {
            return NONE;
        }
        return values()[id];
    }

    /*? if neoforge {*/
    public static final StreamCodec<ByteBuf, AiStatus> STREAM_CODEC = ByteBufCodecs.INT.map(AiStatus::fromId, Enum::ordinal);
    /*?}*/
}
