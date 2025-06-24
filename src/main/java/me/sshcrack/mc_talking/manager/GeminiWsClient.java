package me.sshcrack.mc_talking.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.capability.EntityDataProvider;
import me.sshcrack.mc_talking.config.AvailableAI;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.gson.BidiGenerateContentSetup;
import me.sshcrack.mc_talking.gson.BidiGenerateContentToolResponse;
import me.sshcrack.mc_talking.gson.ClientMessages;
import me.sshcrack.mc_talking.gson.RealtimeInput;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import me.sshcrack.websocket_lib.lib.client.WebSocketClient;
import me.sshcrack.websocket_lib.lib.handshake.ServerHandshake;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

public class GeminiWsClient extends WebSocketClient {
    boolean setupComplete;
    boolean isInitiatingConnection = false;
    boolean shouldReconnect = false;


    GeminiStream stream;
    ServerPlayer initialPlayer;
    TalkingManager manager;
    private final List<short[]> pending_prompt = new ArrayList<>();    // Audio batching variables
    private final List<String> pendingSystemText = new ArrayList<>();


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
            EntityDataProvider.getFromEntity(entity).ifPresent(provider -> {
                var sessionToken = provider.getSessionToken();
                if (!sessionToken.isBlank()) {
                    setup.sessionResumption = new BidiGenerateContentSetup.SessionResumptionConfig(sessionToken);
                }
            });
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

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        isInitiatingConnection = false;

        send();
    }

    private String currMsg = "";

    public void onUsageMetadata(JsonObject obj) {
        McTalking.LOGGER.info("Gemini usage metadata: {}", obj.toString());
    }

    public void onSessionResumptionUpdate(String newHandle, boolean resumable) {
        if(!resumable)
            return;
        EntityDataProvider.getFromEntity(this.manager.entity).ifPresent(provider -> {
            provider.setSessionToken(newHandle);
        });
    }

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

    public void onGeneratedText(String text) {
        var hasTextEnabled = CONFIG.modality.get() == ModalityModes.TEXT || CONFIG.modality.get() == ModalityModes.TEXT_AND_AUDIO;
        if(!hasTextEnabled)
            return;

        currMsg += text;
    }

    public void onTurnComplete() {
        McTalking.LOGGER.info("Gemini turn complete");
        AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));
    }

    public void onGeneratedAudio(byte[] data, int sampleRate) {
        var isJustStarted = stream.addGeminiPcmWithPitch(data, sampleRate);
        if (isJustStarted)
            AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.TALKING));
    }

    public void onSetupComplete() {
        McTalking.LOGGER.info("Gemini setup complete");
        if (!pendingSystemText.isEmpty()) {
            for (String text : pendingSystemText) {
                addSystemText(text);
            }
            pendingSystemText.clear();
        }

        if (!pending_prompt.isEmpty()) {
            for (short[] data : pending_prompt) {
                addPromptAudio(data);
            }
            pending_prompt.clear();
        }
    }

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

        if (reason.contains("BidiGenerateContent session not found")) {
            EntityDataProvider.getFromEntity(manager.entity).ifPresent(provider -> {
                provider.setSessionToken("");
            });
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
            pendingSystemText.add(newStatusPrompt);
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

    public void addPromptAudio(short[] audio) {
        var input = new RealtimeInput();
        input.audio = new RealtimeInput.Blob("audio/pcm;rate=48000", audio);
        if (sentGeneratingStatus)
            AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));


        if (!setupComplete || this.isClosed()) {
            pending_prompt.add(audio);
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
