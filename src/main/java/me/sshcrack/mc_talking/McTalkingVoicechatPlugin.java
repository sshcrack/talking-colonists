package me.sshcrack.mc_talking;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import me.sshcrack.mc_talking.internal.audio.SpeechEnvelope;
import me.sshcrack.mc_talking.internal.audio.VoiceDucking;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.internal.api.PlayerSpeechServiceBackend;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.conversations.memory.CitizenMemoryGenerator;
import me.sshcrack.mc_talking.conversations.memory.PlayerConversationMemoryGenerator;
import me.sshcrack.mc_talking.internal.audio.VoicechatAccess;
import me.sshcrack.mc_talking.pregen.PregenerationPlayback;
import net.minecraft.server.level.ServerPlayer;

@SuppressWarnings("unused")
@ForgeVoicechatPlugin
public class McTalkingVoicechatPlugin implements VoicechatPlugin {
    public static final int TARGET_SAMPLE_RATE = 48000;
    public static final String DIRECT_PLAYER_DIALOG = "ptc_dialog";
    public static final String CITIZEN_CONVERSATION = "ctc_dialog";

    @Override
    public String getPluginId() {
        return McTalking.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        McTalking.LOGGER.info("Initializing Voicechat Plugin");
    }

    public void onStop(VoicechatServerStoppedEvent event) {
        CitizenMemoryGenerator.stopAllGenerators();
        PlayerConversationMemoryGenerator.stopAllGenerators();
        VoicechatAccess.set(null);
    }

    public void onServerStarted(VoicechatServerStartedEvent event) {
        McTalking.LOGGER.info("Voicechat Server Started");
        VoicechatServerApi vcApi = event.getVoicechat();
        VoicechatAccess.set(vcApi);

        VolumeCategory directDialog = vcApi.volumeCategoryBuilder()
                .setId(DIRECT_PLAYER_DIALOG)
                .setName("P2CitizenDialog")
                .setDescription("The volume of the citizens voice when talking directly to the player (so when the player used the Talking Device)")
                .build();
        VolumeCategory citizenConversation = vcApi.volumeCategoryBuilder()
                .setId(CITIZEN_CONVERSATION)
                .setName("C2CitizenDialog")
                .setDescription("The volume of the citizens voice when talking in a conversation with other citizens (so not directly to the player)")
                .build();

        vcApi.registerVolumeCategory(directDialog);
        vcApi.registerVolumeCategory(citizenConversation);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, event -> {
            short[] pcm = event.getRawAudio();
            SpeechEnvelope.accept(event.getEntityId(), pcm);
            event.setRawAudio(VoiceDucking.apply(event.getEntityId(), VoiceDucking.isCitizen(event.getEntityId()),
                    McTalkingClient.localConversationPartner(), duckedGain(), pcm));
        });
        // Locational voices are citizen conversations and anchored addon turns.
        registration.registerEvent(ClientReceiveSoundEvent.LocationalSound.class, event ->
                event.setRawAudio(VoiceDucking.apply(event.getId(), true,
                        McTalkingClient.localConversationPartner(), duckedGain(), event.getRawAudio())));
        registration.registerEvent(MicrophonePacketEvent.class, this::handleMicPacket);
        registration.registerEvent(PlayerDisconnectedEvent.class, this::onPlayerDisconnected);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
        registration.registerEvent(VoicechatServerStoppedEvent.class, this::onStop);
    }

    private static float duckedGain() {
        return (float) McTalkingConfig.INSTANCE.instance().otherCitizensVolumeWhileTalking;
    }

    public void handleMicPacket(MicrophonePacketEvent event) {
        var sender = event.getSenderConnection();
        if (sender == null || sender.isDisabled()) return;

        var vcPlayer = sender.getPlayer();
        if (vcPlayer == null || !(vcPlayer.getPlayer() instanceof ServerPlayer player)) return;

        var packet = event.getPacket();
        byte[] opusData = packet.getOpusEncodedData();
        // An addon-started speech capture owns the microphone, whispering or in a group alike.
        if (PlayerSpeechServiceBackend.acceptMicrophoneOpus(player.getUUID(), opusData)) return;

        if (packet.isWhispering()) return;
        if (sender.isInGroup() && !McTalkingConfig.INSTANCE.instance().respondInGroups) return;

        var manager = ConversationManager.getReadyInputClientForPlayer(player.getUUID());
        if (manager == null) {
            // Pregenerated clips have no Live client and therefore own their small decoder locally.
            // Barge-in still uses decoded PCM, never Opus packet length as speech evidence.
            PregenerationPlayback.onPlayerOpusPacket(player, opusData);
            return;
        }

        boolean speechCandidate = manager.acceptMicrophoneOpus(opusData);
        // A cached clip can overlap takeover startup briefly. Reuse the exact decoded decision from
        // the direct Live client rather than decoding the same packet twice.
        PregenerationPlayback.onPlayerVoicePacket(player, speechCandidate);
    }

    private void onPlayerDisconnected(PlayerDisconnectedEvent event) {
        PregenerationPlayback.onPlayerDisconnected(event.getPlayerUuid());
        PlayerSpeechServiceBackend.onPlayerLeft(event.getPlayerUuid());
    }

    public static boolean shouldDisableColoniesTicks(ServerPlayer player) {
        var vcApi = VoicechatAccess.get();
        if (vcApi == null) return false;
        var conn = vcApi.getConnectionOf(player.getUUID());
        return conn != null && conn.isDisabled()
                && McTalkingConfig.INSTANCE.instance().modality == ModalityModes.AUDIO;
    }
}
