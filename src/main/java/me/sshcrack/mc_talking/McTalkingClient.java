package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import static me.sshcrack.mc_talking.MineColoniesTalkingCitizens.aiStatus;

@Mod(value = MineColoniesTalkingCitizens.MODID, dist = Dist.CLIENT)
public class McTalkingClient {

    public McTalkingClient(ModContainer container) {
        NeoForge.EVENT_BUS.register(this);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public void onDisconnect(LevelEvent.Unload e) {
        aiStatus.clear();
    }

    @SubscribeEvent
    public void onRenderName(RenderNameTagEvent event) {
        var entity = event.getEntity();
        var m = Minecraft.getInstance();
        if (!(entity instanceof AbstractCivilianEntity citizen))
            return;

        assert m.player != null;
        if (entity.isInvisibleTo(m.player))
            return;

        var status = aiStatus.get(citizen.getUUID());
        if (status == AiStatus.NONE || status == null)
            return;

        var text = Component.literal(" (")
                .append(Component.translatable("mc_talking.ai_status." + status.name().toLowerCase()))
                .append(Component.literal(")"))
                .withStyle(ChatFormatting.GRAY);

        event.setContent(event.getContent().copy().append(text));
    }
}
