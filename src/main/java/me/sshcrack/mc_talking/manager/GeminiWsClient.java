package me.sshcrack.mc_talking.manager;

import com.google.gson.JsonObject;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.gemini_live_lib.websocket.handshake.ServerHandshake;
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

import java.util.ArrayList;
import java.util.List;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;
import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

public class GeminiWsClient extends GeminiLiveClient {
    boolean setupComplete;
    boolean isInitiatingConnection = false;
    boolean shouldReconnect = false;

    public static boolean quotaExceeded = false;

    GeminiStream stream;
    ServerPlayer initialPlayer;
    TalkingManager manager;
    private final List<short[]> pending_prompt = new ArrayList<>();    // Audio batching variables
    private final List<String> pendingSystemText = new ArrayList<>();

    private final List<short[]> audioBatch = Collections.synchronizedList(new ArrayList<>());
    private static final long BATCH_TIMEOUT = 100; // 100ms batch window
    private static final int MAX_BATCH_SIZE = 5; // Maximum number of audio packets in a batch
    private volatile Timer batchTimer;
    private volatile TimerTask currentBatchTask;
    private final Object batchLock = new Object();

    private static String getUrl() {
        return "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + CONFIG.geminiApiKey.get();
    }

    //TODO: Don't send packets to all players
    public GeminiWsClient(TalkingManager manager, ServerPlayer player) {
        super(URI.create(getUrl()));
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
    public void onOpen(ServerHandshake handshakeData) {
        isInitiatingConnection = false;
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
        if(!resumable)
            return;
        EntityDataProvider.getFromEntity(this.manager.entity).ifPresent(provider -> {
            provider.setSessionToken(newHandle);
        });
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
        if(!hasTextEnabled)
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

    @Override
    public JsonObject onFunctionCall(String name, @Nullable JsonObject args) {
        var colony = this.manager.entity.getCitizenColonyHandler().getColony();

        var action = AITools.registeredFunctions.get(name);
        if (action == null) {
            McTalking.LOGGER.warn("Unknown function call: {}", name);
            return null;
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        String newContent = new String(bytes.array(), StandardCharsets.UTF_8);
        onMessage(newContent);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        super.onClose(code, reason, remote);

        //vcApi.getAudioConverter().shortsToBytes(data)
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

    @Override
    public void addPromptAudio(short[] audio) {
        var input = new RealtimeInput();
        var byteAudio = vcApi.getAudioConverter().shortsToBytes(audio);
        input.audio = new RealtimeInput.Blob("audio/pcm;rate=48000", byteAudio);

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
        // Clean up timer resources
        if (batchTimer != null) {
            batchTimer.cancel();
            batchTimer = null;
        }
        if (currentBatchTask != null) {
            currentBatchTask.cancel();
            currentBatchTask = null;
        }

        stream.close();
        super.close();
    }

    /**
     * Batches audio data and sends it when a batch is complete or times out
     *
     * @param audio The audio data to batch
     */
    public void batchAudio(short[] audio) {
        boolean batchFull;
        boolean isFirstElement;

        synchronized (batchLock) {
            // Add to batch
            audioBatch.add(audio);

            // Check if batch is full
            batchFull = audioBatch.size() >= MAX_BATCH_SIZE;
            isFirstElement = audioBatch.size() == 1;
        }

        if (batchFull) {
            // Process and send the batch immediately
            sendCurrentBatch();
        } else if (isFirstElement) {
            // If this is the first element in the batch, start the timer
            scheduleFlushTimer();
        }
    }

    /**
     * Schedules a timer to flush the current batch after the timeout period
     */
    private void scheduleFlushTimer() {
        // Cancel any existing task
        if (currentBatchTask != null) {
            currentBatchTask.cancel();
        }

        // Create new task
        currentBatchTask = new TimerTask() {
            @Override
            public void run() {
                if (!audioBatch.isEmpty()) {
                    sendCurrentBatch();
                }
            }
        };

        // Initialize timer if needed
        if (batchTimer == null) {
            batchTimer = new Timer("AudioBatchTimer", true);
        }

        // Schedule the task
        batchTimer.schedule(currentBatchTask, BATCH_TIMEOUT);
    }

    /**
     * Combines and sends the current batch of audio
     */
    private void sendCurrentBatch() {
        List<short[]> batchCopy;

        synchronized (batchLock) {
            if (audioBatch.isEmpty()) return;

            // Create a copy of the batch to work with
            batchCopy = new ArrayList<>(audioBatch);
            // Clear the original batch immediately to allow new additions
            audioBatch.clear();
        }

        // Process the copy outside the synchronized block
        int totalLength = 0;
        for (short[] audioData : batchCopy) {
            totalLength += audioData.length;
        }

        short[] combinedAudio = new short[totalLength];
        int position = 0;

        for (short[] audioData : batchCopy) {
            System.arraycopy(audioData, 0, combinedAudio, position, audioData.length);
            position += audioData.length;
        }

        // Send the combined audio
        addPromptAudio(combinedAudio);
    }
}
