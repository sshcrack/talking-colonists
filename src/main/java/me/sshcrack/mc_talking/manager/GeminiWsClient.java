package me.sshcrack.mc_talking.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import me.sshcrack.mc_talking.Config;
import me.sshcrack.mc_talking.MineColoniesTalkingCitizens;
import me.sshcrack.mc_talking.ModAttachmentTypes;
import me.sshcrack.mc_talking.gson.BidiGenerateContentSetup;
import me.sshcrack.mc_talking.gson.ClientMessages;
import me.sshcrack.mc_talking.gson.RealtimeInput;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GeminiWsClient extends WebSocketClient {
    boolean setupComplete;
    boolean isInitiatingConnection = false;
    boolean shouldReconnect = false;

    public static boolean quotaExceeded = false;

    GeminiStream stream;
    ServerPlayer initialPlayer;
    TalkingManager manager;
    private final List<short[]> pending_prompt = new ArrayList<>();    // Audio batching variables
    private final List<short[]> audioBatch = Collections.synchronizedList(new ArrayList<>());
    private static final long BATCH_TIMEOUT = 100; // 100ms batch window
    private static final int MAX_BATCH_SIZE = 5; // Maximum number of audio packets in a batch
    private volatile Timer batchTimer;
    private volatile TimerTask currentBatchTask;
    private final Object batchLock = new Object();

    private static String getUrl() {
        return "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + Config.GEMINI_API_KEY.get();
    }

    //TODO: Don't send packets to all players
    public GeminiWsClient(TalkingManager manager, ServerPlayer player) {
        super(URI.create(getUrl()));
        this.manager = manager;
        stream = new GeminiStream(manager.channel);
        this.initialPlayer = player;
        PacketDistributor.sendToAllPlayers(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        isInitiatingConnection = false;
        var setup = new BidiGenerateContentSetup("models/" + Config.currentAIModel.getName());
        setup.generationConfig.responseModalities = List.of("AUDIO");
        setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
        setup.generationConfig.speechConfig.language_code = Config.language;

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
        setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig.voice_name = Config.currentAIModel.getRandomVoice(uuid, female);

        setup.realtimeInputConfig = new BidiGenerateContentSetup.RealtimeInputConfig();


        //TODO: Allow citizens to speak for themselves
        //setup.realtimeInputConfig.turnCoverage = BidiGenerateContentSetup.RealtimeInputConfig.TurnCoverage.TURN_INCLUDES_ALL_INPUT;

        var sys = new BidiGenerateContentSetup.SystemInstruction();
        //TODO change player when other player is talking to AI
        var prompt = CitizenContextUtils.generateCitizenRoleplayPrompt(manager.entity.getCitizenData(), initialPlayer);
        var p = new BidiGenerateContentSetup.SystemInstruction.Part(prompt);
        sys.parts.add(p);

        setup.systemInstruction = sys;
        setup.tools.addAll(AITools.getAllTools());

        send(ClientMessages.setup(setup));
    }

    @Override
    public void onMessage(String message) {
        var p = JsonParser.parseString(message);
        if (!p.isJsonObject())
            return;
        var outer = p.getAsJsonObject();
        if (outer.has("setupComplete")) {
            MineColoniesTalkingCitizens.LOGGER.info("Gemini setup complete");
            setupComplete = true;
            if (!pending_prompt.isEmpty()) {
                for (short[] data : pending_prompt) {
                    addPromptAudio(data);
                }
                pending_prompt.clear();
            }
            return;
        }


        if (!setupComplete)
            return;


        if (outer.has("usageMetadata")) {
            MineColoniesTalkingCitizens.LOGGER.info("Gemini usage metadata: {}", outer.get("usageMetadata").toString());
        }

        if (outer.has("sessionResumptionUpdate")) {
            var obj = outer.get("sessionResumptionUpdate").getAsJsonObject();
            if (!obj.has("newHandle") || !obj.get("newHandle").isJsonPrimitive())
                return;

            if (!obj.has("resumable") || !obj.get("resumable").getAsBoolean())
                return;

            var handle = obj.get("newHandle").getAsString();
            this.manager.entity.setData(ModAttachmentTypes.SESSION_TOKEN, handle);
        }

        if (outer.has("toolCall")) {
            System.out.println("Tool call: " + message);
            var obj = outer.getAsJsonObject("toolCall");
            if (!obj.has("functionCalls") || !obj.get("functionCalls").isJsonArray())
                return;

            var functionCalls = obj.getAsJsonArray("functionCalls");
            for (JsonElement fnCall : functionCalls) {
                if (!fnCall.isJsonObject())
                    continue;

                var objFnCall = fnCall.getAsJsonObject();
                if (!objFnCall.has("name") || !objFnCall.get("name").isJsonPrimitive())
                    continue;

                var name = objFnCall.get("name").getAsString();
                var action = AITools.registeredFunctions.get(name);
                if (action == null) {
                    MineColoniesTalkingCitizens.LOGGER.warn("Unknown function call: {}", name);
                    continue;
                }

                action.action().accept(this.manager.entity);
            }

            return;
        }

        if (outer.has("serverContent") && outer.get("serverContent").isJsonObject()) {
            var obj = outer.getAsJsonObject("serverContent");
            if (obj.has("turnComplete") && obj.get("turnComplete").getAsBoolean()) {
                MineColoniesTalkingCitizens.LOGGER.info("Gemini turn complete");
                PacketDistributor.sendToAllPlayers(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));
                return;
            }

            if (obj.has("generationComplete") && obj.get("generationComplete").getAsBoolean()) {
                MineColoniesTalkingCitizens.LOGGER.info("Gemini generation complete");
                PacketDistributor.sendToAllPlayers(new AiStatusPayload(manager.entity.getUUID(), AiStatus.TALKING));
                return;
            }

            if (obj.has("modelTurn")) {
                var modelTurn = obj.getAsJsonObject("modelTurn");
                if (modelTurn.has("parts")) {
                    var parts = modelTurn.getAsJsonArray("parts");
                    for (var part : parts) {
                        if (!part.isJsonObject())
                            continue;

                        var pObj = part.getAsJsonObject();
                        if (!pObj.has("inlineData") || !pObj.get("inlineData").isJsonObject())
                            continue;

                        var inlineData = pObj.getAsJsonObject("inlineData");
                        if (!inlineData.has("data") || !inlineData.get("data").isJsonPrimitive())
                            continue;

                        var mimeType = inlineData.get("mimeType").getAsString();
                        if (!mimeType.contains("audio/pcm")) {
                            MineColoniesTalkingCitizens.LOGGER.warn("Invalid mime type: {}", inlineData.get("mimeType").getAsString());
                            continue;
                        }

                        var sampleRateStr = mimeType.split("rate=")[1];
                        var sampleRate = Integer.parseInt(sampleRateStr);

                        var data = Base64.getDecoder().decode(inlineData.get("data").getAsString());
                        stream.addGeminiPcm(data, sampleRate);
                    }
                }
            } else {
                MineColoniesTalkingCitizens.LOGGER.warn("Unknown message: {}", message);
            }
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        String newContent = new String(bytes.array(), StandardCharsets.UTF_8);
        onMessage(newContent);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        isInitiatingConnection = false;
        if (code != 1000 && code != 1001) {
            PacketDistributor.sendToAllPlayers(new AiStatusPayload(manager.entity.getUUID(), AiStatus.ERROR));
        }
        if(reason.contains("You exceeded your current quota, please")) {
            quotaExceeded = true;
            MineColoniesTalkingCitizens.LOGGER.warn("Quota exceeded for Gemini API, please check your API key and usage limits.");
            PacketDistributor.sendToAllPlayers(new AiStatusPayload(manager.entity.getUUID(), AiStatus.QUOTA_EXCEEDED));
        }

        MineColoniesTalkingCitizens.LOGGER.info("GeminiWsClient closed: " + reason + " and code " + code);
    }

    @Override
    public void onError(Exception ex) {
        PacketDistributor.sendToAllPlayers(new AiStatusPayload(manager.entity.getUUID(), AiStatus.ERROR));
        MineColoniesTalkingCitizens.LOGGER.error("Error in GeminiWsClient", ex);
    }

    boolean sentGeneratingStatus = false;
    long lastReconnectTime = 0;

    public void addPromptAudio(short[] audio) {
        var input = new RealtimeInput();
        input.audio = new RealtimeInput.Blob("audio/pcm;rate=48000", audio);
        if (sentGeneratingStatus)
            PacketDistributor.sendToAllPlayers(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING));


        if (!setupComplete || this.isClosed()) {
            pending_prompt.add(audio);
            if (!this.isOpen() && !isInitiatingConnection) {
                if (shouldReconnect) {
                    if (System.currentTimeMillis() - lastReconnectTime < 2500) {
                        MineColoniesTalkingCitizens.LOGGER.warn("Reconnecting too frequently, skipping this attempt");
                        return;
                    }
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
