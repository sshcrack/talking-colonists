package me.sshcrack.mc_talking.network;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.interaction.TalkToCitizenHandler;
import me.sshcrack.mc_talking.config.McTalkingConfig;
/*? if forge {*/
/*import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;
*//*?}*/
/*? if neoforge {*/
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
/*?}*/

/**
 * Client -> server: "I pressed the talk-to-citizen keybind while looking at this entity."
 *
 * <p>The client only picks a candidate entity to keep the request cheap; the server never
 * trusts that choice and independently re-resolves the entity id, checks its type, distance,
 * and full conversation eligibility before doing anything (see {@link TalkToCitizenHandler}).</p>
 */
public record TalkToCitizenPayload(int citizenEntityId) /*? if neoforge {*/ implements CustomPacketPayload/*?}*/ {
    /*? if forge {*/
    /*public static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(McTalking.MODID, "talk_to_citizen");
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private static int id = 0;

    public static void encode(TalkToCitizenPayload msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.citizenEntityId());
    }

    public static TalkToCitizenPayload decode(FriendlyByteBuf buf) {
        return new TalkToCitizenPayload(buf.readVarInt());
    }

    public static void handle(TalkToCitizenPayload msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                handleOnServer(sender, msg.citizenEntityId());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void registerMessages() {
        CHANNEL.registerMessage(id++, TalkToCitizenPayload.class, TalkToCitizenPayload::encode, TalkToCitizenPayload::decode, TalkToCitizenPayload::handle);
    }

    public static void send(int citizenEntityId) {
        CHANNEL.sendToServer(new TalkToCitizenPayload(citizenEntityId));
    }
    *//*?}*/

    /*? if neoforge {*/
    public static final Type<TalkToCitizenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(McTalking.MODID, "talk_to_citizen"));
    public static final StreamCodec<ByteBuf, TalkToCitizenPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            TalkToCitizenPayload::new, TalkToCitizenPayload::citizenEntityId);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerMessages() {
        // Intentionally empty so we do not get a compile error on neoforge
    }

    public static void send(int citizenEntityId) {
        PacketDistributor.sendToServer(new TalkToCitizenPayload(citizenEntityId));
    }
    /*?}*/

    /**
     * Server-side handling shared by both loaders: re-resolves and re-validates the citizen
     * from scratch. The client-supplied entity id is only ever treated as a hint.
     */
    public static void handleOnServer(ServerPlayer sender, int citizenEntityId) {
        if (!(sender.level() instanceof ServerLevel level)) return;

        var entity = level.getEntity(citizenEntityId);
        if (!(entity instanceof AbstractEntityCitizen citizen) || !entity.isAlive()) return;

        double maxDistance = McTalkingConfig.INSTANCE.instance().maxConversationDistance;
        boolean withinRange = sender.distanceToSqr(citizen) <= maxDistance * maxDistance;

        TalkToCitizenHandler.attempt(sender, citizen, withinRange);
    }
}
