package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler;
import com.minecolonies.core.colony.CitizenData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = CitizenData.class, remap = false)
public interface CitizenDataAccessor {
    @Accessor("citizenChatOptions")
    Map<Component, IInteractionResponseHandler> getCitizenChatOptions();
}
