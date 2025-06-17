package me.sshcrack.mc_talking.network;

import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

public class AiStatusPayload {
    public static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(McTalking.MODID, "ai_status");
    
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    
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
    
    public static void encode(AiStatusPayload msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.citizen);
        buf.writeEnum(msg.status);
    }
    
    public static AiStatusPayload decode(FriendlyByteBuf buf) {
        UUID citizen = buf.readUUID();
        AiStatus status = buf.readEnum(AiStatus.class);
        return new AiStatusPayload(citizen, status);
    }
    
    public static void handle(AiStatusPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ConversationManager.updateAiStatus(msg.citizen, msg.status);
        });
        ctx.get().setPacketHandled(true);
    }
      public static void registerMessages() {
        CHANNEL.registerMessage(0, AiStatusPayload.class, AiStatusPayload::encode, AiStatusPayload::decode, AiStatusPayload::handle);
    }
      public static void sendToAll(AiStatusPayload packet) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), packet);
    }
}
