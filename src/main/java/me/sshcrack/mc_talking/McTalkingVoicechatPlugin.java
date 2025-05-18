package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

@SuppressWarnings("unused")
@ForgeVoicechatPlugin
public class McTalkingVoicechatPlugin implements VoicechatPlugin {
    public static VoicechatServerApi vcApi;

    @Override
    public String getPluginId() {
        return MinecoloniesTalkingCitizens.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        MinecoloniesTalkingCitizens.LOGGER.info("Initializing Voicechat Plugin");
    }

    public void onServerStart(VoicechatServerStartedEvent event) {
        MinecoloniesTalkingCitizens.LOGGER.info("Voicechat Server Started");
        vcApi = event.getVoicechat();
    }


    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::handleMicPacket);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStart);
    }

    public void handleMicPacket(MicrophonePacketEvent event) {
        var sender = event.getSenderConnection();
        if (sender == null)
            return;

        var packet = event.getPacket();
        if (packet.isWhispering())
            return;

        if (sender.isDisabled())
            return;


        var vcPlayer = sender.getPlayer();
        if (sender.isInGroup() && !Config.respondInGroups)
            return;

        var player = (ServerPlayer) vcPlayer.getPlayer();
        var l = player.level();


    }
}
