package me.sshcrack.mc_talking;

import com.mojang.serialization.Codec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.attachment.AttachmentType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class ModAttachmentTypes {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(ForgeRegistries.ATTACHMENT_TYPES, McTalking.MODID);

    public static final Supplier<AttachmentType<String>> SESSION_TOKEN = ATTACHMENT_TYPES.register(
            "session_token",
            () -> AttachmentType.builder(() -> "").serialize(Codec.STRING).build()
    );

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
