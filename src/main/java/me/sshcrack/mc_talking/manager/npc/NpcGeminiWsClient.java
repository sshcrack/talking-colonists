package me.sshcrack.mc_talking.manager.npc;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.ICitizenData;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.AvailableAI;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.manager.GeminiStream;
import me.sshcrack.mc_talking.manager.SessionManager;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;
import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/**
 * GeminiWsClient specialized for NPC-to-NPC conversations.
 * 
 * Each NPC in a multi-NPC conversation has its own NpcGeminiWsClient instance,
 * which maintains its own session with the Gemini Live API. This allows each
 * NPC to have independent context, personality, and memory.
 * 
 * <h2>Key Differences from GeminiWsClient:</h2>
 * <ul>
 *   <li>Uses compact NPC system prompts designed for multi-NPC context</li>
 *   <li>Handles responses by broadcasting them to other NPCs</li>
 *   <li>Supports audio output to entity voice channel</li>
 *   <li>Integrated with NPCConversation for message routing</li>
 * </ul>
 */
public class NpcGeminiWsClient extends GeminiLiveClient {
    
    private final NPCConversation conversation;
    private final ICitizenData citizenData;
    private final UUID npcId;
    private final List<String> pendingSystemText;
    
    // Audio output support
    private EntityAudioChannel channel;
    private GeminiStream stream;
    
    private boolean setupComplete;
    private boolean isInitiatingConnection;
    private boolean shouldReconnect;
    private long lastReconnectTime;
    private String currentMessage;
    private boolean sessionAcquired;
    
    /**
     * Creates a new NPC Gemini WebSocket client.
     * 
     * @param conversation The parent conversation this NPC belongs to
     * @param citizenData The citizen data for this NPC
     * @throws IllegalStateException if a session slot cannot be acquired
     */
    public NpcGeminiWsClient(NPCConversation conversation, ICitizenData citizenData) {
        super(CONFIG.geminiApiKey.get());
        
        this.conversation = conversation;
        this.citizenData = citizenData;
        this.npcId = NPCConversationUtils.generateNpcId(citizenData);
        this.pendingSystemText = Collections.synchronizedList(new ArrayList<>());
        this.setupComplete = false;
        this.isInitiatingConnection = false;
        this.shouldReconnect = false;
        this.lastReconnectTime = 0;
        this.currentMessage = "";
        this.sessionAcquired = false;
        
        // Acquire a session slot from the central manager
        if (!SessionManager.acquireSession(npcId, this::close)) {
            throw new IllegalStateException("Cannot create NpcGeminiWsClient: session limit reached");
        }
        this.sessionAcquired = true;
        
        // Initialize audio channel for the NPC entity
        initializeAudioChannel();
        
        McTalking.LOGGER.info("Created NpcGeminiWsClient for NPC: {}", citizenData.getName());
    }
    
    /**
     * Initializes the audio channel for the NPC entity.
     */
    private void initializeAudioChannel() {
        if (vcApi == null) {
            McTalking.LOGGER.warn("VoiceChat API not available, NPC {} will not have audio output", citizenData.getName());
            return;
        }
        
        var entityOpt = citizenData.getEntity();
        if (entityOpt.isEmpty()) {
            McTalking.LOGGER.warn("NPC {} has no entity, cannot create audio channel", citizenData.getName());
            return;
        }
        
        var entity = entityOpt.get();
        
        // Create a unique UUID for the channel (use random to avoid conflicts)
        UUID channelId = UUID.randomUUID();
        channel = vcApi.createEntityAudioChannel(channelId, vcApi.fromEntity(entity));
        
        if (channel == null) {
            McTalking.LOGGER.warn("Failed to create audio channel for NPC: {}", citizenData.getName());
            return;
        }
        
        channel.setWhispering(true);
        stream = new GeminiStream(channel, entity.getUUID());
        
        // Set pitch based on NPC characteristics
        if (citizenData.isChild() && !citizenData.isFemale()) {
            stream.setPitch(0.8f);
        }
        
        McTalking.LOGGER.info("Audio channel created for NPC: {}", citizenData.getName());
    }
    
    /**
     * Gets the UUID of this NPC.
     * 
     * @return The NPC's UUID
     */
    public UUID getNpcId() {
        return npcId;
    }
    
    /**
     * Gets the citizen data for this NPC.
     * 
     * @return The citizen data
     */
    public ICitizenData getCitizenData() {
        return citizenData;
    }
    
    @Override
    public BidiGenerateContentSetup getSetup() {
        var setup = new BidiGenerateContentSetup("models/" + CONFIG.currentAiModel.get().getName());
        
        // Use configured modality for NPC conversations (supports audio output)
        var modality = CONFIG.modality.get();
        setup.generationConfig.responseModalities = modality.getModalities();
        
        if (modality == ModalityModes.TEXT_AND_AUDIO) {
            setup.outputAudioTranscription = new JsonObject();
        }
        
        // Configure speech settings if audio is enabled
        if (modality != ModalityModes.TEXT) {
            setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
            setup.generationConfig.speechConfig.language_code = CONFIG.language.get();
            
            var entityOpt = citizenData.getEntity();
            UUID entityUuid = entityOpt.isPresent() ? entityOpt.get().getUUID() : npcId;
            
            setup.generationConfig.speechConfig.voice_config = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.VoiceConfig();
            setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.PrebuiltVoiceConfig();
            setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig.voice_name = 
                CONFIG.currentAiModel.get().getRandomVoice(entityUuid, citizenData.isFemale());
        }
        
        // Build the compact system prompt for this NPC in the multi-NPC context
        var sys = new BidiGenerateContentSetup.SystemInstruction();
        String prompt = NPCPromptGenerator.generateCompactNPCPrompt(
            citizenData, 
            conversation.getAllNPCs()
        );
        var p = new BidiGenerateContentSetup.SystemInstruction.Part(prompt);
        sys.parts.add(p);
        
        setup.systemInstruction = sys;
        
        // Add enabled tools (function calling)
        setup.tools.addAll(AITools.getEnabledTools());
        if (CONFIG.currentAiModel.get() == AvailableAI.Flash2_5 && CONFIG.enableFunctionWorkaround.get()) {
            setup.tools.add(BidiGenerateContentSetup.Tool.googleSearch());
        }
        
        return setup;
    }
    
    @Override
    public void onUsageMetadata(JsonObject obj) {
        McTalking.LOGGER.debug("NPC {} Gemini usage metadata: {}", citizenData.getName(), obj);
    }
    
    @Override
    public void onSessionResumptionUpdate(String newHandle, boolean resumable) {
        // NPC sessions don't persist session tokens between sessions
    }
    
    @Override
    public void onGenerationComplete() {
        McTalking.LOGGER.debug("NPC {} generation complete", citizenData.getName());
        
        // Flush any remaining audio
        if (stream != null) {
            stream.flushAudio();
        }
        
        if (!currentMessage.isBlank()) {
            // Broadcast this NPC's response to other NPCs in the conversation
            conversation.broadcastToOthers(npcId, currentMessage.trim());
            currentMessage = "";
        }
    }
    
    @Override
    public void onInterrupted() {
        McTalking.LOGGER.debug("NPC {} generation interrupted", citizenData.getName());
        
        // Stop audio playback
        if (stream != null) {
            stream.stop();
        }
        
        if (!currentMessage.isBlank()) {
            // Still broadcast partial message
            conversation.broadcastToOthers(npcId, currentMessage.trim());
            currentMessage = "";
        }
    }
    
    @Override
    public void onGeneratedText(String text) {
        var hasTextEnabled = CONFIG.modality.get() == ModalityModes.TEXT || CONFIG.modality.get() == ModalityModes.TEXT_AND_AUDIO;
        if (!hasTextEnabled)
            return;
        
        currentMessage += text;
    }
    
    @Override
    public void onOutputTranscription(String transcription) {
        // Capture transcription for audio mode
        currentMessage += transcription;
    }
    
    @Override
    public void onTurnComplete() {
        McTalking.LOGGER.debug("NPC {} turn complete", citizenData.getName());
    }
    
    @Override
    public void onGeneratedAudio(byte[] data, int sampleRate) {
        // Send audio to the entity's voice channel
        if (stream == null) {
            return;
        }
        
        var entityOpt = citizenData.getEntity();
        UUID entityUuid = entityOpt.isPresent() ? entityOpt.get().getUUID() : npcId;
        
        var isJustStarted = stream.addGeminiPcmWithPitch(data, sampleRate);
        if (isJustStarted) {
            // Notify that the NPC is now talking
            AiStatusPayload.sendToAll(new AiStatusPayload(entityUuid, AiStatus.TALKING));
        }
    }
    
    @Override
    public void onSetupComplete() {
        McTalking.LOGGER.info("NPC {} Gemini setup complete", citizenData.getName());
        setupComplete = true;
        
        // Send initial status
        var entityOpt = citizenData.getEntity();
        if (entityOpt.isPresent()) {
            AiStatusPayload.sendToAll(new AiStatusPayload(entityOpt.get().getUUID(), AiStatus.LISTENING));
        }
        
        // Process any pending messages
        synchronized (pendingSystemText) {
            if (!pendingSystemText.isEmpty()) {
                List<String> textToProcess = new ArrayList<>(pendingSystemText);
                pendingSystemText.clear();
                
                for (String text : textToProcess) {
                    sendTextInput(text);
                }
            }
        }
    }
    
    @Override
    public JsonObject onFunctionCall(String name, @Nullable JsonObject args) {
        var colony = citizenData.getColony();
        
        var action = AITools.registeredFunctions.get(name);
        if (action == null) {
            McTalking.LOGGER.warn("NPC {} - Unknown function call: {}", citizenData.getName(), name);
            return null;
        }
        
        // For NPC function calls, we need an entity reference
        var entityOpt = citizenData.getEntity();
        if (entityOpt.isEmpty()) {
            McTalking.LOGGER.warn("NPC {} has no entity for function call: {}", citizenData.getName(), name);
            return null;
        }
        
        return action.execute(entityOpt.get(), colony, args);
    }
    
    @Override
    public void onQuotaExceeded() {
        McTalking.LOGGER.warn("NPC {} - Quota exceeded for Gemini API", citizenData.getName());
    }
    
    @Override
    public void onClose(int code, String reason, boolean remote) {
        super.onClose(code, reason, remote);
        
        if (reason.contains("BidiGenerateContent session not found")) {
            new Thread(() -> {
                if (!isOpen() || !isInitiatingConnection) {
                    reconnect();
                    isInitiatingConnection = true;
                }
            }).start();
            return;
        }
        
        if (code != 1000 && code != 1001) {
            McTalking.LOGGER.warn("NPC {} - GeminiWsClient closed: {} and code {}", 
                citizenData.getName(), reason, code);
        } else {
            McTalking.LOGGER.info("NPC {} - GeminiWsClient closed normally: {}", 
                citizenData.getName(), reason);
        }
    }
    
    @Override
    public void onError(Exception ex) {
        McTalking.LOGGER.error("NPC {} - Error in GeminiWsClient: ", citizenData.getName(), ex);
    }
    
    /**
     * Adds system text to be sent to the Gemini session.
     * This is used for receiving messages from other NPCs or external sources.
     * 
     * @param text The text to send
     */
    public void addSystemText(String text) {
        if (!setupComplete || this.isClosed()) {
            synchronized (pendingSystemText) {
                pendingSystemText.add(text);
            }
            
            if (!this.isOpen() && !isInitiatingConnection) {
                if (shouldReconnect) {
                    if (System.currentTimeMillis() - lastReconnectTime < 5000) {
                        return;
                    }
                    
                    McTalking.LOGGER.warn("NPC {} - Connection lost, attempting to reconnect...", 
                        citizenData.getName());
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
        
        sendTextInput(text);
    }
    
    /**
     * Sends text input to the Gemini session.
     * 
     * @param text The text to send
     */
    private void sendTextInput(String text) {
        var input = new RealtimeInput();
        input.text = text;
        send(ClientMessages.input(input));
    }
    
    @Override
    public void close() {
        super.close();
        
        // Release the session slot
        if (sessionAcquired) {
            SessionManager.releaseSession(npcId);
            sessionAcquired = false;
        }
        
        // Clean up audio resources
        if (stream != null) {
            stream.close();
            stream = null;
        }
        channel = null;
        
        // Send status update
        var entityOpt = citizenData.getEntity();
        if (entityOpt.isPresent()) {
            AiStatusPayload.sendToAll(new AiStatusPayload(entityOpt.get().getUUID(), AiStatus.NONE));
        }
        
        McTalking.LOGGER.info("Closed NpcGeminiWsClient for NPC: {}", citizenData.getName());
    }
}
