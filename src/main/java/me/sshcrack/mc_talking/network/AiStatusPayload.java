package me.sshcrack.mc_talking.network;

import me.sshcrack.mc_talking.McTalking;
/*? if forge {*/
/*import me.sshcrack.mc_talking.ConversationManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;
*//*?}*/
/*? if neoforge {*/
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
/*?}*/

import java.util.UUID;

public record AiStatusPayload(UUID citizen, AiStatus status) /*? if neoforge {*/ implements CustomPacketPayload/*?}*/ {
    /*? if forge {*/
    /*public static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(McTalking.MODID, "ai_status");
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static int id = 0;
    *//*?}*/

    /*? if neoforge {*/
    public static final Type<AiStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(McTalking.MODID, "ai_status"));
    public static final StreamCodec<ByteBuf, AiStatusPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            AiStatusPayload::citizen,
            AiStatus.STREAM_CODEC,
            AiStatusPayload::status,
            AiStatusPayload::new
    );
    /*?}*/

    /*? if forge {*/
    /*public static void encode(AiStatusPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.citizen);
        buf.writeEnum(msg.status);
    }

    public static AiStatusPayload decode(FriendlyByteBuf buf) {
        return new AiStatusPayload(buf.readUUID(), buf.readEnum(AiStatus.class));
    }

    public static void handle(AiStatusPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> me.sshcrack.mc_talking.McTalkingClient.updateAiStatus(msg.citizen, msg.status));
        ctx.get().setPacketHandled(true);
    }

    public static void registerMessages() {
        CHANNEL.registerMessage(id++, AiStatusPayload.class, AiStatusPayload::encode, AiStatusPayload::decode, AiStatusPayload::handle);
    }
    *//*?}*/

    /*? if neoforge {*/
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerMessages() {
        // Intentionally empty so we do not get a compile error on neoforge
    }
    /*?}*/
}
