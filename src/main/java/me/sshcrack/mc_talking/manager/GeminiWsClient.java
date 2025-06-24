package me.sshcrack.mc_talking.manager;

import com.google.gson.JsonObject;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.ModAttachmentTypes;
import me.sshcrack.mc_talking.config.AvailableAI;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;
import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

public class GeminiWsClient extends GeminiLiveClient {
    boolean setupComplete;
    boolean isInitiatingConnection = false;
    boolean shouldReconnect = false;


    GeminiStream stream;
    ServerPlayer initialPlayer;
    TalkingManager manager;
    private final List<short[]> pending_prompt = Collections.synchronizedList(new ArrayList<>());    // Audio batching variables
    private final List<String> pendingSystemText = Collections.synchronizedList(new ArrayList<>());


    //TODO: Don't send packets to all players
    public GeminiWsClient(TalkingManager manager, ServerPlayer player) {
        super(CONFIG.geminiApiKey.get());
        this.manager = manager;
        stream = new GeminiStream(manager.channel);

        var isFemale = manager.entity.getCitizenData().isFemale();
        var isChild = manager.entity.getCitizenData().isChild();
        if (isChild && !isFemale)
            stream.setPitch(0.8f); // Increase pitch

        this.initialPlayer = player;
        AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));
    }

    @Override
    public BidiGenerateContentSetup getSetup() {
        var setup = new BidiGenerateContentSetup("models/" + CONFIG.currentAiModel.get().getName());

        var modality = CONFIG.modality.get();
        setup.generationConfig.responseModalities = modality.getModes();
        if (CONFIG.currentAiModel.get() == AvailableAI.Flash2_5) {
            setup.generationConfig.responseModalities = List.of("AUDIO");
        }

        if (modality != ModalityModes.TEXT) {
            setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
            setup.generationConfig.speechConfig.language_code = CONFIG.language.get();
            var entity = this.manager.entity;
            var female = entity.getCitizenData().isFemale();
            var uuid = entity.getUUID();

            setup.sessionResumption = new BidiGenerateContentSetup.SessionResumptionConfig();
            if (entity.hasData(ModAttachmentTypes.SESSION_TOKEN)) {
                var sessionToken = entity.getData(ModAttachmentTypes.SESSION_TOKEN);
                if (!sessionToken.isBlank()) {
                    setup.sessionResumption = new BidiGenerateContentSetup.SessionResumptionConfig(sessionToken);
                }
            }
            setup.generationConfig.speechConfig.voice_config = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.VoiceConfig();
            setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.PrebuiltVoiceConfig();
            setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig.voice_name = CONFIG.currentAiModel.get().getRandomVoice(uuid, female);

        }

        setup.realtimeInputConfig = new BidiGenerateContentSetup.RealtimeInputConfig();


        //TODO: Allow citizens to speak for themselves
        //setup.realtimeInputConfig.turnCoverage = BidiGenerateContentSetup.RealtimeInputConfig.TurnCoverage.TURN_INCLUDES_ALL_INPUT;

        var sys = new BidiGenerateContentSetup.SystemInstruction();
        //TODO change player when other player is talking to AI
        var prompt = CitizenContextUtils.generateCitizenRoleplayPrompt(manager.entity.getCitizenData(), initialPlayer);
        var p = new BidiGenerateContentSetup.SystemInstruction.Part(prompt);
        sys.parts.add(p);

        setup.systemInstruction = sys;
        setup.tools.addAll(AITools.getEnabledTools());
        if (CONFIG.currentAiModel.get() == AvailableAI.Flash2_5 && CONFIG.enableFunctionWorkaround.get())
            setup.tools.add(BidiGenerateContentSetup.Tool.googleSearch());

        return setup;
    }

    private String currMsg = "";

    @Override
    public void onUsageMetadata(JsonObject obj) {
        McTalking.LOGGER.info("Gemini usage metadata: {}", obj.toString());
    }

    @Override
    public void onSessionResumptionUpdate(String newHandle, boolean resumable) {
        if (!resumable)
            return;

        this.manager.entity.setData(ModAttachmentTypes.SESSION_TOKEN.get(), newHandle);
    }

    @Override
    public void onGenerationComplete() {
        McTalking.LOGGER.info("Gemini generation complete");


        stream.flushAudio();
        var player = ConversationManager.getPlayerForEntity(manager.entity.getUUID());
        if (player == null)
            return;
        var sPlayer = initialPlayer.server.getPlayerList().getPlayer(player);
        if (sPlayer == null || currMsg.isBlank())
            return;
        sPlayer.sendSystemMessage(manager.entity.getDisplayName().copy().append(": ").append(Component.literal(currMsg.trim())));
        currMsg = "";
    }

    @Override
    public void onInterrupted() {
        McTalking.LOGGER.info("Gemini generation interrupted");
        stream.stop();

        var player = ConversationManager.getPlayerForEntity(manager.entity.getUUID());
        if (player == null)
            return;
        var sPlayer = initialPlayer.server.getPlayerList().getPlayer(player);
        if (sPlayer == null || currMsg.isBlank())
            return;
        sPlayer.sendSystemMessage(manager.entity.getDisplayName().copy().append(": ").append(Component.literal(currMsg.trim())));
        currMsg = "";
    }

    @Override
    public void onGeneratedText(String text) {
        var hasTextEnabled = CONFIG.modality.get() == ModalityModes.TEXT || CONFIG.modality.get() == ModalityModes.TEXT_AND_AUDIO;
        if (!hasTextEnabled)
            return;

        currMsg += text;
    }

    @Override
    public void onTurnComplete() {
        McTalking.LOGGER.info("Gemini turn complete");
        AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));
    }

    @Override
    public void onGeneratedAudio(byte[] data, int sampleRate) {
        var isJustStarted = stream.addGeminiPcmWithPitch(data, sampleRate);
        if (!isJustStarted)
            return;

        AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.TALKING));
    }

    @Override
    public void onSetupComplete() {
        McTalking.LOGGER.info("Gemini setup complete");
        setupComplete = true;
        
        synchronized (pendingSystemText) {
            if (!pendingSystemText.isEmpty()) {
                List<String> textToProcess = new ArrayList<>(pendingSystemText);
                pendingSystemText.clear();
                
                for (String text : textToProcess) {
                    var input = new RealtimeInput();
                    input.text = text;
                    send(ClientMessages.input(input));
                }
            }
        }

        synchronized (pending_prompt) {
            if (!pending_prompt.isEmpty()) {
                List<short[]> audioToProcess = new ArrayList<>(pending_prompt);
                pending_prompt.clear();
                
                for (short[] data : audioToProcess) {
                    var input = new RealtimeInput();
                    var byteAudio = vcApi.getAudioConverter().shortsToBytes(data);
                    input.audio = new RealtimeInput.Blob("audio/pcm;rate=48000", byteAudio);
                    send(ClientMessages.input(input));
                }
            }
        }
    }

    @Override
    public JsonObject onFunctionCall(String name, @Nullable JsonObject args) {
        var colony = this.manager.entity.getCitizenColonyHandler().getColony();

        var action = AITools.registeredFunctions.get(name);
        if (action == null) {
            McTalking.LOGGER.warn("Unknown function call: {}", name);
            return null;
        }

        return action.execute(this.manager.entity, colony, args);
    }

    @Override
    public void onQuotaExceeded() {
        McTalking.LOGGER.warn("Quota exceeded for Gemini API, please check your API key and usage limits.");
        AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.QUOTA_EXCEEDED));
        try {
            initialPlayer.sendSystemMessage(Component.literal("Quota exceeded for Gemini API, please check your API key and usage limits."));
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        super.onClose(code, reason, remote);

        //vcApi.getAudioConverter().shortsToBytes(data)
        if (reason.contains("BidiGenerateContent session not found")) {
            manager.entity.setData(ModAttachmentTypes.SESSION_TOKEN, "");
            new Thread(() -> {
                if (!isOpen() || !isInitiatingConnection) {
                    reconnect();
                    isInitiatingConnection = true;
                }
            }).start();
            return;
        }
        if (code != 1000 && code != 1001) {
            AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.ERROR));
        }

        McTalking.LOGGER.info("GeminiWsClient closed: {} and code {}", reason, code);
    }

    @Override
    public void onError(Exception ex) {
        AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.ERROR));
        ex.printStackTrace();
    }

    boolean sentGeneratingStatus = false;
    long lastReconnectTime = 0;

    public void addSystemText(String newStatusPrompt) {
        if (!setupComplete || this.isClosed()) {
            synchronized (pendingSystemText) {
                pendingSystemText.add(newStatusPrompt);
            }
            if (!this.isOpen() && !isInitiatingConnection) {
                if (shouldReconnect) {
                    if (System.currentTimeMillis() - lastReconnectTime < 5000)
                        return;

                    McTalking.LOGGER.warn("Connection lost, attempting to reconnect...");
                    lastReconnectTime = System.currentTimeMillis();
                    reconnect();
                } else {
                    connect();
                    shouldReconnect = true;
                }

                isInitiatingConnection = true;
            }
            return;
        }

        var input = new RealtimeInput();
        input.text = newStatusPrompt;

        send(ClientMessages.input(input));
    }

    @Override
    public void addPromptAudio(short[] audio) {
        var input = new RealtimeInput();
        var byteAudio = vcApi.getAudioConverter().shortsToBytes(audio);
        input.audio = new RealtimeInput.Blob("audio/pcm;rate=48000", byteAudio);

        if (sentGeneratingStatus)
            AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));


        if (!setupComplete || this.isClosed()) {
            synchronized (pending_prompt) {
                pending_prompt.add(audio);
            }

            if (!this.isOpen() && !isInitiatingConnection) {
                if (shouldReconnect) {
                    if (System.currentTimeMillis() - lastReconnectTime < 5000)
                        return;

                    McTalking.LOGGER.warn("Connection lost, attempting to reconnect...");
                    lastReconnectTime = System.currentTimeMillis();
                    reconnect();
                } else {
                    connect();
                    shouldReconnect = true;
                }

                isInitiatingConnection = true;
            }
            return;
        }

        send(ClientMessages.input(input));
    }


    @Override
    public void close() {
        super.close();
        stream.close();
    }
}