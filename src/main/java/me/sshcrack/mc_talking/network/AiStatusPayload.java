package me.sshcrack.mc_talking.network;

import io.netty.buffer.ByteBuf;
import me.sshcrack.mc_talking.McTalking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record AiStatusPayload(
        UUID citizen,
        AiStatus status
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AiStatusPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(McTalking.MODID, "ai_status"));

    public static final StreamCodec<ByteBuf, AiStatusPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            AiStatusPayload::citizen,
            AiStatus.STREAM_CODEC,
            AiStatusPayload::status,
            AiStatusPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static void sendToAll(AiStatusPayload packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }
}
