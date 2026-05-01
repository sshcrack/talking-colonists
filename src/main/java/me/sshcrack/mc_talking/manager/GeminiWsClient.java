package me.sshcrack.mc_talking.manager;

import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.gemini_live_lib.websocket.handshake.ServerHandshake;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;
import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

public abstract class GeminiWsClient extends GeminiLiveClient {
    /**
     * This variable is used to track if the quota has been exceeded
     */
    private static boolean quotaExceeded;

    private boolean isInitiatingConnection = false;
    private boolean hasMadeInitialConnection = false;
    private boolean generationComplete = false;
    /**
     * Whether the AI has started generating audio at least once (used to gate onGenerationPaused).
     */
    private boolean sentGeneratingStatus = false;
    private long lastReconnectTime = 0;

    /**
     * Accumulates AI-generated text/transcription for the current turn to display in chat.
     */
    private String currentTurnTranscript = "";

    private final GeminiStream stream;
    private final AbstractEntityCitizen entity;
    private final AudioChannel channel;
    private final OpusDecoder decoder;
    private final List<short[]> pendingPrompt = Collections.synchronizedList(new ArrayList<>());    // Audio batching variables
    private final List<String> pendingSystemText = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pendingTextAfterTalking = Collections.synchronizedList(new ArrayList<>());

    @Nullable
    private VisibleCitizenStatus lastStatus;

    public AbstractEntityCitizen getEntity() {
        return entity;
    }

    // AudioProvider creates channels/decoders so this client can be tested/mockable
    protected GeminiWsClient(AudioProvider audioProvider, AbstractEntityCitizen entity) {
        super(CONFIG.geminiApiKey.get());
        this.entity = entity;
        this.channel = audioProvider.createChannel();
        this.decoder = audioProvider.createDecoder();
        stream = new GeminiStream(channel);
        stream.setOnPause(this::onStreamPause);

        var isFemale = entity.getCitizenData().isFemale();
        var isChild = entity.getCitizenData().isChild();
        if (isChild && !isFemale)
            stream.setPitch(1.2f); // Increase pitch
    }

    public boolean shouldResumeAndSaveSession() {
        return true;
    }

    @Nullable
    public VisibleCitizenStatus getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(@Nullable VisibleCitizenStatus lastStatus) {
        this.lastStatus = lastStatus;
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
            var entity = this.entity;
            var female = entity.getCitizenData().isFemale();
            var uuid = entity.getUUID();

            setup.sessionResumption = new BidiGenerateContentSetup.SessionResumptionConfig();
            var mem = ((CitizenDataMemoryExtended) entity.getCitizenData()).mc_talking$getOrInitializeMemory();
            var sessionToken = mem.getSessionToken();
            if (!sessionToken.isBlank() && shouldResumeAndSaveSession()) {
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

        var p = new BidiGenerateContentSetup.SystemInstruction.Part(getSystemPrompt());
        sys.parts.add(p);

        setup.systemInstruction = sys;
        setup.tools.addAll(AITools.getEnabledTools());

        return setup;
    }

    protected abstract String getSystemPrompt();

    /**
     * Resolves the active player for this conversation so that generated text and transcriptions
     * can be forwarded as chat messages. Subclasses may override this to provide the player
     * directly without going through {@link ConversationManager}.
     */
    @Nullable
    protected ServerPlayer resolveActivePlayer() {
        var playerUUID = ConversationManager.getPlayerForEntity(entity.getUUID());
        if (playerUUID == null) return null;
        return Objects.requireNonNull(entity.level().getServer()).getPlayerList().getPlayer(playerUUID);
    }

    protected void onStreamPause() {
        if (generationComplete) {
            onConversationEnded();
        } else {
            AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.THINKING);
        }
    }

    protected void onConversationEnded() {
        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.LISTENING);
        if (!pendingTextAfterTalking.isEmpty()) {
            String message = String.join("\n", pendingTextAfterTalking);
            pendingTextAfterTalking.clear();
            var input = new RealtimeInput();
            input.text = message;

            send(ClientMessages.input(input));
        }
    }

    protected void onGenerationStarted() {
        sentGeneratingStatus = true;
        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.TALKING);
    }

    protected void onGenerationPaused() {
        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.THINKING);
    }

    protected abstract void onQuotaExceededEvent(String message);

    protected abstract void onErrorEvent(Exception ex);

    @Override
    public void send(String text) {
        super.send(text);
        generationComplete = false;
    }

    @Override
    public void onUsageMetadata(JsonObject obj) {
        //McTalking.LOGGER.info("Gemini usage metadata: {}", obj.toString());
    }

    @Override
    public void onSessionResumptionUpdate(String newHandle, boolean resumable) {
        if (!resumable || !shouldResumeAndSaveSession())
            return;

        var mem = ((CitizenDataMemoryExtended) entity.getCitizenData()).mc_talking$getOrInitializeMemory();
        mem.setSessionToken(newHandle);
    }

    @Override
    public void onGenerationComplete() {
        McTalking.LOGGER.info("Gemini generation complete");

        stream.flushAudio();
        var sPlayer = resolveActivePlayer();
        if (sPlayer == null || currentTurnTranscript.isBlank())
            return;
        if (CONFIG.modality.get() == ModalityModes.TEXT || CONFIG.modality.get() == ModalityModes.TEXT_AND_AUDIO) {
            sPlayer.sendSystemMessage(entity.getDisplayName().copy().append(": ").append(Component.literal(currentTurnTranscript.trim())));
        }

        currentTurnTranscript = "";
    }

    @Override
    public void onInterrupted() {
        McTalking.LOGGER.info("Gemini generation interrupted");
        stream.stop();

        var sPlayer = resolveActivePlayer();
        if (sPlayer == null || currentTurnTranscript.isBlank())
            return;
        sPlayer.sendSystemMessage(entity.getDisplayName().copy().append(": ").append(Component.literal(currentTurnTranscript.trim())));
        currentTurnTranscript = "";
    }

    @Override
    public void onGeneratedText(String text) {
        var hasTextEnabled = CONFIG.modality.get() == ModalityModes.TEXT || CONFIG.modality.get() == ModalityModes.TEXT_AND_AUDIO;
        if (!hasTextEnabled)
            return;

        currentTurnTranscript += text;
    }

    @Override
    public void onOutputTranscription(String transcription) {
        currentTurnTranscript += transcription;
    }

    @Override
    public void onTurnComplete() {
        McTalking.LOGGER.info("Gemini turn complete");
        generationComplete = true;
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
        onGenerationStarted();
    }

    @Override
    public void onSetupComplete() {
        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.LISTENING);

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
                McTalking.LOGGER.info("Sending {} pending audio inputs", pendingPrompt.size());
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
        var colony = this.entity.getCitizenColonyHandler().getColony();

        var action = AITools.registeredFunctions.get(name);
        if (action == null) {
            McTalking.LOGGER.warn("Unknown function call: {}", name);
            return null;
        }

        McTalking.LOGGER.info("Entity {} has called tool {}", entity.getStringUUID(), name);
        return action.execute(this.entity, colony, args);
    }

    @Override
    public void onQuotaExceeded() {
        McTalking.LOGGER.warn("Quota exceeded for Gemini API, please check your API key and usage limits.");
        quotaExceeded = true;
        onQuotaExceededEvent("Quota exceeded for Gemini API, please check your API key and usage limits.");
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        super.onClose(code, reason, remote);

        if (reason.contains("BidiGenerateContent session not found")) {
            var mem = ((CitizenDataMemoryExtended) entity.getCitizenData()).mc_talking$getOrInitializeMemory();
            mem.setSessionToken("");
            new Thread(() -> {
                if (!isOpen() || !isInitiatingConnection) {
                    reconnect();
                }
            }).start();
            return;
        }

        if (code != 1000 && code != 1001) {
            onErrorEvent(new RuntimeException("Close with code " + code + ": " + reason));
        }

        if (code == 1000) {
            McTalking.LOGGER.info("GeminiWsClient closed normally: {}", reason);
        } else {
            McTalking.LOGGER.warn("GeminiWsClient closed: {} and code {}", reason, code);
        }
    }

    @Override
    public void onError(Exception ex) {
        onErrorEvent(ex);
        McTalking.LOGGER.error("Error in GeminiWsClient: ", ex);
    }

    @Override
    public void addPromptAudio(short[] audio) {
        var input = new RealtimeInput();
        var byteAudio = vcApi.getAudioConverter().shortsToBytes(audio);
        input.audio = new RealtimeInput.Blob("audio/pcm;rate=48000", byteAudio);

        if (sentGeneratingStatus)
            onGenerationPaused();


        if (!isSetupComplete() || this.isClosed()) {
            synchronized (pendingPrompt) {
                pendingPrompt.add(audio);
            }

            if (!this.isOpen() && !isInitiatingConnection && !quotaExceeded) {
                if (!hasMadeInitialConnection) {
                    connect();
                } else {
                    if (System.currentTimeMillis() - lastReconnectTime < 5000)
                        return;

                    McTalking.LOGGER.warn("Connection lost, attempting to reconnect...");
                    reconnect();
                }
            }
            return;
        }

        send(ClientMessages.input(input));
    }

    public void addPromptTextAfterTalkingComplete(String text) {
        if (sentGeneratingStatus)
            onGenerationPaused();

        if (!isSetupComplete() || this.isClosed()) {
            synchronized (pendingSystemText) {
                pendingSystemText.add(text);
            }

            if (!this.isOpen() && !isInitiatingConnection && !quotaExceeded) {
                if (!hasMadeInitialConnection) {
                    connect();
                } else {
                    if (System.currentTimeMillis() - lastReconnectTime < 5000)
                        return;

                    McTalking.LOGGER.warn("Connection lost, attempting to reconnect...");
                    reconnect();
                }
            }
            return;
        }

        pendingTextAfterTalking.add(text);
    }

    @Override
    public void connect() {
        isInitiatingConnection = true;
        hasMadeInitialConnection = true;
        super.connect();
    }

    @Override
    public void reconnect() {
        isInitiatingConnection = true;
        lastReconnectTime = System.currentTimeMillis();
        super.reconnect();
    }

    public void promptAudioOpus(byte[] audio) {
        if (decoder == null) return;
        var raw = decoder.decode(audio);
        addPromptAudio(raw);
    }


    @Override
    public void close() {
        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.NONE);
        super.close();
        stream.close();
    }

    /**
     * If set to true, avoids to send new status updates while the conversation is active, which can be used to reduce status update spam when the AI is generating multiple turns in a row.
     *
     * @return false, if new status updates should NOT be sent
     */
    public boolean sendStatusUpdates() {
        return true;
    }
}
