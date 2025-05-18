package me.sshcrack.mc_talking.manager;

import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import me.sshcrack.mc_talking.MinecoloniesTalkingCitizens;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class TalkingManager {
    GeminiWsClient client;
    LocationalAudioChannel channel;
    Entity entity;

    public TalkingManager(Entity entity) {
        MinecoloniesTalkingCitizens.LOGGER.info("Creating TalkingManager for entity: {}", entity.getStringUUID());
        if(vcApi == null)
            throw new IllegalStateException("Voicechat API is not initialized");

        this.entity = entity;
        var vcLevel = vcApi.fromServerLevel(entity.level());
        var pos = vcApi.createPosition(entity.getX(), entity.getY(), entity.getZ());
        channel = vcApi.createLocationalAudioChannel(UUID.randomUUID(), vcLevel, pos);

        client = new GeminiWsClient(this);
    }

    public void updatePos() {
        if(client.isClosed() || client.stream.player == null || !client.stream.player.isStarted())
            return;

        var pos = vcApi.createPosition(entity.getX(), entity.getY(), entity.getZ());
        channel.updateLocation(pos);
    }

    public void prompt(String text) {
        if(client.isClosed())
            client.connect();

        client.addPrompt(text);
    }

    public void close() {
        client.close();
        channel = null;
    }
}
