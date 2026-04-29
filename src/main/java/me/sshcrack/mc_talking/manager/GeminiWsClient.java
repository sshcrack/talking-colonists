package me.sshcrack.mc_talking.manager;

import com.google.gson.JsonObject;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.gemini_live_lib.websocket.handshake.ServerHandshake;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;
import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

public class GeminiWsClient extends GeminiLiveClient {
    /**
     * This variable is used to track if the quota has been exceeded
     */
    private static boolean quotaExceeded;

    private boolean shouldReconnect = false;
    private boolean isInitiatingConnection = false;

    private final GeminiStream stream;
    private final ServerPlayer initialPlayer;
    private final TalkingManager manager;
    private final List<short[]> pendingPrompt = Collections.synchronizedList(new ArrayList<>());    // Audio batching variables
    private final List<String> pendingSystemText = Collections.synchronizedList(new ArrayList<>());


    //TODO: Don't send packets to all players
    public GeminiWsClient(TalkingManager manager, ServerPlayer player) {
        super(CONFIG.geminiApiKey.get());
        this.manager = manager;
        stream = new GeminiStream(manager.channel);
        stream.setOnPause(() -> Objects.requireNonNull(player.getServer()).execute(() -> AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING))));

        var isFemale = manager.entity.getCitizenData().isFemale();
        var isChild = manager.entity.getCitizenData().isChild();
        if (isChild && !isFemale)
            stream.setPitch(1.2f); // Increase pitch

        this.initialPlayer = player;
        AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));
    }

    @Override
    public BidiGenerateContentSetup getSetup() {
        var setup = new BidiGenerateContentSetup("models/" + CONFIG.currentAiModel.get().getName());

        var modality = CONFIG.modality.get();
        setup.generationConfig.responseModalities = modality.getModalities();

        if (modality == ModalityModes.TEXT_AND_AUDIO) {
            setup.outputAudioTranscription = new JsonObject();
        }

        if (modality != ModalityModes.TEXT) {
            setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
            setup.generationConfig.speechConfig.language_code = CONFIG.language.get();
            var entity = this.manager.entity;
            var female = entity.getCitizenData().isFemale();
            var uuid = entity.getUUID();

            setup.sessionResumption = new BidiGenerateContentSetup.SessionResumptionConfig();
            var sessionToken = ConversationManager.getSessionToken(uuid);
            if (!sessionToken.isBlank()) {
                setup.sessionResumption = new BidiGenerateContentSetup.SessionResumptionConfig(sessionToken);
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
        //TODO actually make a summary of the conversation after it has ended

        Map<UUID, String> interestedParties = new HashMap<>();
        interestedParties.put(initialPlayer.getUUID(), initialPlayer.getName().getString());

        var promptView = CitizenPromptViewFactory.create(manager.entity.getCitizenData(), interestedParties, initialPlayer);
        var prompt = CitizenPromptService.generateCitizenRoleplayPrompt(promptView);
        var p = new BidiGenerateContentSetup.SystemInstruction.Part(prompt);
        sys.parts.add(p);

        setup.systemInstruction = sys;
        setup.tools.addAll(AITools.getEnabledTools());

        return setup;
    }

    private String currMsg = "";

    @Override
    public void onUsageMetadata(JsonObject obj) {
        //McTalking.LOGGER.info("Gemini usage metadata: {}", obj.toString());
    }

    @Override
    public void onSessionResumptionUpdate(String newHandle, boolean resumable) {
        if (!resumable)
            return;

        ConversationManager.setSessionToken(this.manager.entity.getUUID(), newHandle);
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
        if (CONFIG.modality.get() == ModalityModes.TEXT || CONFIG.modality.get() == ModalityModes.TEXT_AND_AUDIO) {
            sPlayer.sendSystemMessage(manager.entity.getDisplayName().copy().append(": ").append(Component.literal(currMsg.trim())));
        }

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
    public void onOutputTranscription(String transcription) {
        currMsg += transcription;
    }

    @Override
    public void onTurnComplete() {
        McTalking.LOGGER.info("Gemini turn complete");
    }

    @Override
    public void onOpen(ServerHandshake data) {
        isInitiatingConnection = false;
        super.onOpen(data);
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

        synchronized (pendingPrompt) {
            if (!pendingPrompt.isEmpty()) {
                List<short[]> audioToProcess = new ArrayList<>(pendingPrompt);
                pendingPrompt.clear();

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
        quotaExceeded = true;
        try {
            initialPlayer.sendSystemMessage(Component.literal("Quota exceeded for Gemini API, please check your API key and usage limits."));
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        super.onClose(code, reason, remote);

        if (reason.contains("BidiGenerateContent session not found")) {
            ConversationManager.setSessionToken(manager.entity.getUUID(), "");
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
            if (initialPlayer.hasPermissions(4) && !quotaExceeded && CONFIG.sendErrorsToPlayers.get())
                initialPlayer.sendSystemMessage(Component.literal("An error occurred in GeminiWsClient with reason " + reason + " and code " + code));
        }

        if (code == 1000) {
            McTalking.LOGGER.info("GeminiWsClient closed normally: {}", reason);
        } else {
            McTalking.LOGGER.warn("GeminiWsClient closed: {} and code {}", reason, code);
        }
    }

    @Override
    public void onError(Exception ex) {
        AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.ERROR));
        if (initialPlayer.hasPermissions(4) && CONFIG.sendErrorsToPlayers.get())
            initialPlayer.sendSystemMessage(Component.literal("An error occurred in GeminiWsClient: " + ex.getMessage()));

        McTalking.LOGGER.error("Error in GeminiWsClient: ", ex);
    }

    boolean sentGeneratingStatus = false;
    long lastReconnectTime = 0;

    @Override
    public void addPromptAudio(short[] audio) {
        var input = new RealtimeInput();
        var byteAudio = vcApi.getAudioConverter().shortsToBytes(audio);
        input.audio = new RealtimeInput.Blob("audio/pcm;rate=48000", byteAudio);

        if (sentGeneratingStatus)
            AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));


        if (!isSetupComplete() || this.isClosed()) {
            synchronized (pendingPrompt) {
                pendingPrompt.add(audio);
            }

            if (!this.isOpen() && !isInitiatingConnection && !quotaExceeded) {
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
