package me.sshcrack.mc_talking.network;

import me.sshcrack.mc_talking.McTalking;
/*? if forge {*/
/*import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
*//*?}*/
/*? if neoforge {*/
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
/*?}*/

import java.util.UUID;

public class AiStatusPayload /*? if neoforge {*/implements CustomPacketPayload/*?}*/ {
    /*? if forge {*/
    /*public static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(McTalking.MODID, "ai_status");
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    *//*?}*/

    /*? if neoforge {*/
    public static final CustomPacketPayload.Type<AiStatusPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(McTalking.MODID, "ai_status"));
    public static final StreamCodec<ByteBuf, AiStatusPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            AiStatusPayload::citizen,
            AiStatus.STREAM_CODEC,
            AiStatusPayload::status,
            AiStatusPayload::new
    );
    /*?}*/

    private final UUID citizen;
    private final AiStatus status;

    public AiStatusPayload(UUID citizen, AiStatus status) {
        this.citizen = citizen;
        this.status = status;
    }

    public UUID citizen() {
        return citizen;
    }

    public AiStatus status() {
        return status;
    }

    /*? if forge {*/
    /*public static void encode(AiStatusPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.citizen);
        buf.writeEnum(msg.status);
    }

    public static AiStatusPayload decode(FriendlyByteBuf buf) {
        return new AiStatusPayload(buf.readUUID(), buf.readEnum(AiStatus.class));
    }

    public static void handle(AiStatusPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ConversationManager.updateAiStatus(msg.citizen, msg.status));
        ctx.get().setPacketHandled(true);
    }

    public static void registerMessages() {
        CHANNEL.registerMessage(0, AiStatusPayload.class, AiStatusPayload::encode, AiStatusPayload::decode, AiStatusPayload::handle);
    }

    public static void sendToAll(AiStatusPayload packet) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToPlayer(ServerPlayer player, AiStatusPayload packet) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToPlayersTrackingEntity(net.minecraft.world.entity.Entity entity, AiStatusPayload packet) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }
    *//*?}*/

    /*? if neoforge {*/
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerMessages() {
    }

    public static void sendToAll(AiStatusPayload packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendToPlayer(ServerPlayer player, AiStatusPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToPlayersTrackingEntity(net.minecraft.world.entity.Entity entity, AiStatusPayload packet) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, packet);
    }
    /*?}*/
}
