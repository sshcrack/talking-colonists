package me.sshcrack.mc_talking.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum AiStatus {
    ERROR,
    THINKING,
    TALKING,
    LISTENING,
    NONE;

    public static final StreamCodec<ByteBuf, AiStatus> STREAM_CODEC = ByteBufCodecs.INT.map(e -> AiStatus.values()[e], Enum::ordinal);
}
