package me.sshcrack.mc_talking.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/// Don't forget to also update `en_us.json`!!
public enum AiStatus {
    ERROR,
    THINKING,
    QUOTA_EXCEEDED,
    TALKING,
    LISTENING,
    NONE;

    public static final StreamCodec<ByteBuf, AiStatus> STREAM_CODEC = ByteBufCodecs.INT.map(e -> AiStatus.values()[e], Enum::ordinal);
}
