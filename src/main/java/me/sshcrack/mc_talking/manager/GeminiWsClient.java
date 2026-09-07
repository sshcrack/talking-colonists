package me.sshcrack.mc_talking.manager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import me.sshcrack.mc_talking.internal.api.AiToolDispatcher;
import me.sshcrack.mc_talking.internal.api.AiToolExecutionContext;
import me.sshcrack.mc_talking.internal.api.AiToolRuntime;
import me.sshcrack.mc_talking.internal.session.ProviderRecoveryController;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.QuotaTracker;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

import me.sshcrack.mc_talking.config.McTalkingConfig;

public abstract class GeminiWsClient extends GeminiLiveClient {
    private static final int MAX_TOTAL_RECOVERY_ATTEMPTS = 6;
    private static final long MAX_RECOVERY_WINDOW_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long GRACEFUL_CLOSE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);
    private static final AiToolDispatcher ADDON_TOOL_DISPATCHER = new AiToolDispatcher();
    private static volatile ScheduledExecutorService RECONNECT_EXECUTOR;

    private static synchronized ScheduledExecutorService getReconnectExecutor() {
        if (RECONNECT_EXECUTOR == null || RECONNECT_EXECUTOR.isShutdown() || RECONNECT_EXECUTOR.isTerminated()) {
            RECONNECT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mc_talking_ws_reconnect");
                t.setDaemon(true);
                return t;
            });
        }
        return RECONNECT_EXECUTOR;
    }


    /**
     * Returns the model name string for quota tracking.
     */
    protected abstract String getModelName();

    private boolean hasMadeInitialConnection = false;
    private volatile boolean reconnectScheduled = false;
    private volatile boolean producedOutputSinceSetup = false;
    private final ProviderRecoveryController recoveryController = new ProviderRecoveryController(
            MAX_TOTAL_RECOVERY_ATTEMPTS, MAX_RECOVERY_WINDOW_MS, System::currentTimeMillis);
    private final List<Consumer<ProviderRecoveryController.Diagnostic>> recoveryObservers =
            Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean closeStarted = new AtomicBoolean(false);
    private final AtomicBoolean providerTerminalEventFired = new AtomicBoolean(false);
    private volatile boolean finalGenerationCompleted = false;
    private final AtomicBoolean gracefulEndRequested = new AtomicBoolean(false);
    private final AtomicBoolean gracefulEndFinished = new AtomicBoolean(false);
    @Nullable
    private ScheduledFuture<?> gracefulEndFuture;
    @Nullable
    private ScheduledFuture<?> reconnectFuture;
    protected boolean generationComplete = false;
    /**
     * Whether the AI has started generating audio at least once (used to gate onGenerationPaused).
     */
    private boolean sentGeneratingStatus = false;
    protected boolean shouldEndConversation = false;

    /**
     * The most recent text submitted via {@link #addPromptTextAfterTalkingComplete}.
     * Saved so that after a session-token invalidation and reconnect the prompt can
     * be re-queued, preventing system-controlled sessions (mumbling / urgent contact)
     * from falling silent.
     */
    @Nullable
    private String lastPromptText = null;

    /**
     * Accumulates AI-generated text/transcription for the current turn to display in chat.
     */
    protected String currentTurnTranscript = "";

    private final String logPrefix;
    private final UUID toolSessionId = UUID.randomUUID();
    private final ThreadLocal<ArrayDeque<ProviderToolCall>> providerToolCalls = new ThreadLocal<>();
    protected final GeminiStream stream;
    @Nullable
    private volatile String selectedVoiceName;
    private final AbstractEntityCitizen entity;
    private final OpusDecoder decoder;
    private final List<short[]> pendingPrompt = Collections.synchronizedList(new ArrayList<>());    // Audio batching variables
    private final List<String> pendingSystemText = Collections.synchronizedList(new ArrayList<>());
    private final List<String> pendingTextAfterTalking = Collections.synchronizedList(new ArrayList<>());
    private final List<Runnable> onCloseActions = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean closeActionsFired = new AtomicBoolean(false);

    private final long sessionStartTimeMs = System.currentTimeMillis();

    public long getSessionStartTimeMs() {
        return sessionStartTimeMs;
    }

    /** Immutable snapshot of generated citizen speech collected for this session. */
    public String getSessionTranscriptSnapshot() {
        synchronized (sessionTranscript) {
            return sessionTranscript.toString();
        }
    }

    @Nullable
    private VisibleCitizenStatus lastStatus;

    public AbstractEntityCitizen getEntity() {
        return entity;
    }

    // AudioProvider creates channels/decoders so this client can be tested/mockable
    protected GeminiWsClient(AudioProvider audioProvider, AbstractEntityCitizen entity) {
        super(McTalkingConfig.INSTANCE.instance().geminiApiKey);
        this.entity = entity;
        this.logPrefix = "[GeminiWsClient, " + entity.getUUID() + "]";
        AudioChannel channel = audioProvider.createChannel();
        this.decoder = audioProvider.createDecoder();
        stream = new GeminiStream(channel);
        stream.setOnPause(this::onStreamPause);

        var citizenData = entity.getCitizenData();
        if (citizenData == null) {
            throw new IllegalArgumentException("CitizenData cannot be null for entity " + entity.getUUID());
        }
        var isFemale = citizenData.isFemale();
        var isChild = citizenData.isChild();
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

    public void addOnCloseAction(Runnable action) {
        onCloseActions.add(action);
    }

    private void fireOnCloseActions() {
        if (!closeActionsFired.compareAndSet(false, true)) {
            return;
        }

        synchronized (onCloseActions) {
            for (Runnable action : onCloseActions) {
                try {
                    action.run();
                } catch (Exception e) {
                    McTalking.LOGGER.error("{} Error executing onClose action", logPrefix, e);
                }
            }
            onCloseActions.clear();
        }
    }

    public void endConversationWhenPossible() {
        this.shouldEndConversation = true;
    }

    private void requestGracefulEndAfterPlayback() {
        if (!gracefulEndRequested.compareAndSet(false, true)) return;
        stream.flushAudio();
        if (!stream.hasPendingPlayback()) {
            finishGracefulEnd();
            return;
        }
        gracefulEndFuture = getReconnectExecutor().schedule(() -> {
            McTalking.LOGGER.warn("{} Graceful close timed out after {} ms; forcing session end",
                    logPrefix, GRACEFUL_CLOSE_TIMEOUT_MS);
            finishGracefulEnd();
        }, GRACEFUL_CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void finishGracefulEnd() {
        if (!gracefulEndFinished.compareAndSet(false, true)) return;
        ScheduledFuture<?> future = gracefulEndFuture;
        if (future != null) {
            future.cancel(false);
            gracefulEndFuture = null;
        }

        var server = entity.level().getServer();
        Runnable finish = () -> {
            var playerUUID = ConversationManager.getPlayerForEntity(entity.getUUID());
            if (playerUUID != null) {
                ConversationManager.endConversation(playerUUID, false);
            } else {
                close();
            }
        };
        if (server != null && !server.isSameThread()) {
            server.execute(finish);
        } else {
            finish.run();
        }
    }

    protected boolean isSessionReadyForInput() {
        return recoveryController.diagnostic().state() == ProviderRecoveryController.State.ACTIVE && !this.isClosed();
    }

    /** Observable bounded-recovery state used by lifecycle/debug ownership code. */
    public ProviderRecoveryController.Diagnostic getRecoveryDiagnostic() {
        return recoveryController.diagnostic();
    }

    public void addRecoveryObserver(Consumer<ProviderRecoveryController.Diagnostic> observer) {
        Objects.requireNonNull(observer, "observer");
        recoveryObservers.add(observer);
        observer.accept(recoveryController.diagnostic());
    }

    /** True only after this client has run its local close/stream cleanup exactly once. */
    public boolean isLifecycleClosed() {
        return closeStarted.get();
    }

    private void transitionRecovery(String reason, Runnable transition) {
        ProviderRecoveryController.Diagnostic before = recoveryController.diagnostic();
        transition.run();
        ProviderRecoveryController.Diagnostic after = recoveryController.diagnostic();
        if (before.state() != after.state() || !Objects.equals(before.detail(), after.detail())) {
            McTalking.LOGGER.info("{} provider session {} -> {} ({})", logPrefix, before.state(), after.state(), reason);
        }
        notifyRecoveryObservers(after);
    }

    private void notifyRecoveryObservers(ProviderRecoveryController.Diagnostic diagnostic) {
        synchronized (recoveryObservers) {
            for (Consumer<ProviderRecoveryController.Diagnostic> observer : recoveryObservers) {
                try {
                    observer.accept(diagnostic);
                } catch (RuntimeException e) {
                    McTalking.LOGGER.warn("{} Recovery observer failed", logPrefix, e);
                }
            }
        }
    }

    private ProviderRecoveryController.State recoveryState() {
        return recoveryController.diagnostic().state();
    }

    private boolean canAttemptRecovery() {
        return !QuotaTracker.isQuotaExceeded(getModelName()) && recoveryController.canRecover();
    }

    private synchronized void ensureConnectionForQueuedInput(String source) {
        if (!canAttemptRecovery()) return;
        ProviderRecoveryController.State state = recoveryState();
        if (this.isOpen()
                || state == ProviderRecoveryController.State.CONNECTING
                || state == ProviderRecoveryController.State.SETTING_UP
                || state == ProviderRecoveryController.State.RECOVERING) {
            return;
        }
        if (!hasMadeInitialConnection) {
            McTalking.LOGGER.info("{} Starting initial websocket connection ({})", logPrefix, source);
            connect();
            return;
        }
        scheduleRecovery(source);
    }

    private synchronized boolean scheduleRecovery(String cause) {
        if (reconnectScheduled || this.isOpen()) return true;

        ProviderRecoveryController.Diagnostic before = recoveryController.diagnostic();
        ProviderRecoveryController.RecoveryAttempt attempt = recoveryController.beginRecovery(cause);
        ProviderRecoveryController.Diagnostic after = attempt.diagnostic();
        if (before.state() != after.state() || !Objects.equals(before.detail(), after.detail())) {
            McTalking.LOGGER.info("{} provider session {} -> {} ({})", logPrefix, before.state(), after.state(), cause);
        }
        notifyRecoveryObservers(after);

        if (!attempt.allowed()) {
            AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.NONE);
            finishProviderTerminal(new RuntimeException(after.detail()));
            return false;
        }

        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.RECONNECTING);
        reconnectScheduled = true;
        reconnectFuture = getReconnectExecutor().schedule(() -> {
            synchronized (GeminiWsClient.this) {
                reconnectScheduled = false;
                reconnectFuture = null;
                ProviderRecoveryController.Diagnostic diagnostic = recoveryController.diagnostic();
                if (diagnostic.terminal() || recoveryController.intentionalClose() || GeminiWsClient.this.isOpen()) return;
            }
            try {
                GeminiWsClient.super.reconnect();
            } catch (RuntimeException e) {
                McTalking.LOGGER.error("{} Provider reconnect attempt failed", logPrefix, e);
                scheduleRecovery("reconnect exception: " + e.getClass().getSimpleName());
            }
        }, attempt.delayMillis(), TimeUnit.MILLISECONDS);
        return true;
    }

    private void finishProviderTerminal(@Nullable Exception error) {
        fireOnCloseActions();
        if (error != null && providerTerminalEventFired.compareAndSet(false, true)) {
            onErrorEvent(error);
        }
    }

    /**
     * Returns the effective modality for this client.
     * Subclasses may override to force a specific modality regardless of the global config.
     * Defaults to the globally configured modality.
     */
    protected ModalityModes getEffectiveModality() {
        return McTalkingConfig.INSTANCE.instance().modality;
    }

    /**
     * Accumulates the AI-generated text across all turns in this session, used for memory generation.
     * Only populated when audio transcription is enabled (i.e. when citizen memory is enabled).
     */
    protected final StringBuilder sessionTranscript = new StringBuilder();

    @Override
    public BidiGenerateContentSetup getSetup() {
        var setup = new BidiGenerateContentSetup("models/" + getModelName());

        var modality = getEffectiveModality();
        setup.generationConfig.responseModalities = modality.getModalities();

        if (modality == ModalityModes.TEXT_AND_AUDIO || McTalkingConfig.INSTANCE.instance().enableConversationSummaryAndMemorize) {
            setup.outputAudioTranscription = new JsonObject();
        }

        if (modality != ModalityModes.TEXT) {
            var citizenData = entity.getCitizenData();
            if (citizenData == null) {
                McTalking.LOGGER.warn("{} CitizenData not available for entity {} during setup, skipping audio config", logPrefix, entity.getUUID());
            } else {
                setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
                setup.generationConfig.speechConfig.language_code = McTalkingConfig.INSTANCE.instance().language;
                var female = citizenData.isFemale();
                var uuid = entity.getUUID();

                setup.sessionResumption = new BidiGenerateContentSetup.SessionResumptionConfig();
                var mem = ((CitizenDataMemoryExtended) citizenData).mc_talking$getOrInitializeMemory();
                var sessionToken = mem.getSessionToken();
                if (!sessionToken.isBlank() && shouldResumeAndSaveSession()) {
                    setup.sessionResumption = new BidiGenerateContentSetup.SessionResumptionConfig(sessionToken);
                }
                setup.generationConfig.speechConfig.voice_config = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.VoiceConfig();
                setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.PrebuiltVoiceConfig();
                var selectedAi = McTalkingConfig.INSTANCE.instance().currentAiModel;
                selectedVoiceName = VoiceSelectionService.select(selectedAi, uuid, female);
                setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig.voice_name = selectedVoiceName;
            }
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
            if (shouldEndConversation) {
                finishGracefulEnd();
            }
        } else if (recoveryState() == ProviderRecoveryController.State.ACTIVE) {
            AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.THINKING);
        }
    }

    protected void onConversationEnded() {
        if (recoveryState() == ProviderRecoveryController.State.ACTIVE) {
            AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.LISTENING);
            flushPendingText();
        }
    }

    /**
     * Immediately sends any buffered {@link #pendingTextAfterTalking} to the API.
     *
     * <p>Called internally by {@link #onConversationEnded()} and may also be
     * invoked externally (e.g. by the peer in a citizen-to-citizen conversation)
     * to release text that was queued early — before the peer's own audio stream
     * had a chance to drain.
     */
    protected void flushPendingText() {
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
        if (recoveryState() == ProviderRecoveryController.State.ACTIVE) {
            AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.TALKING);
        }
    }

    protected void onGenerationPaused() {
        if (recoveryState() == ProviderRecoveryController.State.ACTIVE) {
            AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.THINKING);
        }
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

        McTalking.LOGGER.info("{} Received session token {}. Saving...", logPrefix, newHandle);
        var mem = ((CitizenDataMemoryExtended) entity.getCitizenData()).mc_talking$getOrInitializeMemory();
        mem.setSessionToken(newHandle);
    }

    @Override
    public void onGenerationComplete() {
        McTalking.LOGGER.info("{} Gemini generation complete", logPrefix);

        producedOutputSinceSetup = true;
        if (shouldEndConversation) finalGenerationCompleted = true;
        stream.flushAudio();

        if (!currentTurnTranscript.isBlank()) {
            if (!sessionTranscript.isEmpty()) sessionTranscript.append("\n");
            sessionTranscript.append(entity.getDisplayName().getString()).append(": ").append(currentTurnTranscript.trim());
        }

        // NOTE: Do NOT clear currentTurnTranscript here.
        // onTurnComplete() fires after onGenerationComplete() and subclasses
        // (e.g. LiveConversationWsClient) need the transcript to forward it to
        // the peer.  Clearing and chat-sending is done in onTurnComplete instead.

        generationComplete = true;
    }

    @Override
    public void onInterrupted() {
        McTalking.LOGGER.info("{} Gemini generation interrupted", logPrefix);
        stream.stop();

        if (!currentTurnTranscript.isBlank()) {
            if (!sessionTranscript.isEmpty()) sessionTranscript.append("\n");
            sessionTranscript.append(entity.getDisplayName().getString()).append(": ").append(currentTurnTranscript.trim());
        }

        var sPlayer = resolveActivePlayer();
        if (currentTurnTranscript.isBlank()) {
            return;
        }

        sendTranscriptToChat(sPlayer);
        currentTurnTranscript = "";
    }

    private void sendTranscriptToChat(@Nullable ServerPlayer sPlayer) {
        var modality = getEffectiveModality();
        var hasTextEnabled = modality == ModalityModes.TEXT || modality == ModalityModes.TEXT_AND_AUDIO;
        if (!hasTextEnabled) return;

        var message = entity.getDisplayName().copy().append(": ").append(Component.literal(currentTurnTranscript.trim()));

        if (sPlayer != null) {
            sPlayer.sendSystemMessage(message);
        } else if (McTalkingConfig.INSTANCE.instance().sendMumblingAndConversationsToChat) {
            var server = entity.level().getServer();
            if (server != null) {
                double range = McTalkingConfig.INSTANCE.instance().citizenInteractionRange * 2;
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.level() == entity.level() && player.distanceTo(entity) <= range) {
                        player.sendSystemMessage(message);
                    }
                }
            }
        }
    }

    @Override
    public void onGeneratedText(String text) {
        if (finalGenerationCompleted) return;
        producedOutputSinceSetup = true;
        var hasTextEnabled = getEffectiveModality() == ModalityModes.TEXT || getEffectiveModality() == ModalityModes.TEXT_AND_AUDIO;
        if (!hasTextEnabled)
            return;

        currentTurnTranscript += text;
    }

    @Override
    public void onOutputTranscription(String transcription) {
        if (finalGenerationCompleted) return;
        producedOutputSinceSetup = true;
        currentTurnTranscript += transcription;
    }

    @Override
    public void onTurnComplete() {
        McTalking.LOGGER.info("{} Gemini turn complete", logPrefix);
        generationComplete = true;

        // Send the transcript to chat and notify subclasses before clearing.
        // This is done here (not in onGenerationComplete) so that subclasses
        // can still act on the transcript via the onTranscriptComplete hook.
        var sPlayer = resolveActivePlayer();
        if (!currentTurnTranscript.isBlank()) {
            sendTranscriptToChat(sPlayer);
            onTranscriptComplete(currentTurnTranscript.trim());
            currentTurnTranscript = "";
        }

        if (shouldEndConversation) {
            finalGenerationCompleted = true;
            requestGracefulEndAfterPlayback();
        }
    }

    /**
     * Called from {@link #onTurnComplete()} with the completed transcript text,
     * right before {@link #currentTurnTranscript} is cleared.
     *
     * <p>The default implementation is a no-op. Subclasses may override this
     * to act on the finished transcript (e.g. forwarding it to a peer).
     *
     * @param transcript the non-empty, trimmed transcript for the just-completed turn
     */
    protected void onTranscriptComplete(String transcript) {
        // no-op by default
    }

    @Override
    public void onOpen(ServerHandshake data) {
        transitionRecovery("websocket opened", () -> recoveryController.markSettingUp("websocket opened"));
        super.onOpen(data);
    }

    @Override
    public void onGeneratedAudio(byte[] data, int sampleRate) {
        if (finalGenerationCompleted) {
            McTalking.LOGGER.debug("{} Dropping audio generated after the requested final turn", logPrefix);
            return;
        }
        producedOutputSinceSetup = true;
        var isJustStarted = stream.addGeminiPcmWithPitch(data, sampleRate);
        if (!isJustStarted)
            return;
        onGenerationStarted();
    }

    /**
     * Sends a text prompt directly to the Gemini API right now, bypassing the
     * "after talking" buffer. If the connection is not yet ready the text is
     * queued and sent as soon as setup completes, just like audio prompts.
     */
    public void addPromptTextImmediate(String text) {
        if (!isSessionReadyForInput()) {
            synchronized (pendingSystemText) {
                pendingSystemText.add(text);
            }
            ensureConnectionForQueuedInput("addPromptTextImmediate");
            return;
        }

        var input = new RealtimeInput();
        input.text = text;
        send(ClientMessages.input(input));
    }

    @Override
    public void onSetupComplete() {
        QuotaTracker.reportSuccess(getModelName());
        producedOutputSinceSetup = false;
        synchronized (this) {
            reconnectScheduled = false;
            if (reconnectFuture != null) {
                reconnectFuture.cancel(false);
                reconnectFuture = null;
            }
        }
        transitionRecovery("setup complete", recoveryController::setupSucceeded);
        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.LISTENING);

        McTalking.LOGGER.info("{} Gemini setup complete", logPrefix);
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
                McTalking.LOGGER.info("{} Sending {} pending audio inputs", logPrefix, pendingPrompt.size());
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
    public void onMessage(String message) {
        ArrayDeque<ProviderToolCall> calls = extractProviderToolCalls(message);
        if (calls.isEmpty()) {
            super.onMessage(message);
            return;
        }

        ArrayDeque<ProviderToolCall> activeCalls = new ArrayDeque<>(calls);
        providerToolCalls.set(activeCalls);
        try {
            super.onMessage(message);
        } finally {
            activeCalls.clear();
            providerToolCalls.remove();
        }
    }

    @Override
    public JsonObject onFunctionCall(String name, @Nullable JsonObject args) {
        // Gemini may batch built-in and addon calls together. Consume every call ID in order so a
        // preceding built-in never shifts the idempotency key used by a later addon command.
        String providerCallId = pollProviderCallId(name);
        var colony = this.entity.getCitizenColonyHandler().getColony();

        var action = AITools.getAction(name);
        var addonAction = AiToolRuntime.findByProviderName(name);
        if (action == null && addonAction == null) {
            McTalking.LOGGER.warn("{} Unknown function call: {}", logPrefix, name);
            var error = new JsonObject();
            error.addProperty("error", "Unknown function: " + name);
            return error;
        }

        var activePlayer = resolveActivePlayer();
        if (action != null && action.isPlayerOnly() && activePlayer == null) {
            McTalking.LOGGER.warn("{} Player-only tool {} called without active player", logPrefix, name);
            var error = new JsonObject();
            error.addProperty("error", "You cannot use this tool until a player is speaking to you directly.");
            return error;
        }

        McTalking.LOGGER.info("{} [TOOL-CALL] Entity {} has called tool {} with parameters {}", logPrefix, entity.getStringUUID(), name, new Gson().toJson(args));
        JsonObject result;
        try {
            if (addonAction != null) {
                var context = new AiToolExecutionContext(toolSessionId, this.entity, colony, activePlayer);
                var endpoint = new AiToolDispatcher.SessionEndpoint() {
                    @Override
                    public UUID sessionId() {
                        return toolSessionId;
                    }

                    @Override
                    public AiToolExecutionContext context() {
                        return context;
                    }

                    @Override
                    public boolean isAvailable() {
                        return isSessionReadyForInput();
                    }

                    @Override
                    public boolean deliver(JsonObject outcome) {
                        return tryDeliverToolOperationOutcome(outcome);
                    }
                };
                result = ADDON_TOOL_DISPATCHER.dispatch(providerCallId, name, args, endpoint);
            } else {
                result = action.execute(this.entity, colony, args);
            }
        } catch (Exception e) {
            McTalking.LOGGER.error("{} [TOOL-CALL] Tool threw an unexpected exception. Params are {}.", logPrefix, (new Gson()).toJson(args), e);
            var error = new JsonObject();
            error.addProperty("error", "A fatal error occurred. Don't call this tool again.");

            return error;
        }

        McTalking.LOGGER.info("{} [TOOL-CALL] Result of {}: {}", logPrefix, name, result);
        return result;
    }

    private String pollProviderCallId(String functionName) {
        ArrayDeque<ProviderToolCall> calls = providerToolCalls.get();
        if (calls == null) return "";
        ProviderToolCall call = calls.pollFirst();
        if (call == null) return "";
        if (!call.name().equals(functionName)) {
            McTalking.LOGGER.warn("{} Provider tool-call ID/name mismatch: expected {}, received {}",
                    logPrefix, call.name(), functionName);
            return "";
        }
        return call.id();
    }

    private boolean tryDeliverToolOperationOutcome(JsonObject outcome) {
        if (!isSessionReadyForInput()) return false;
        try {
            var input = new RealtimeInput();
            input.text = "A previously accepted Talking Colonists tool operation has finished. "
                    + "Use this structured result for the current conversation only:\n" + outcome;
            if (!isSessionReadyForInput()) return false;
            send(ClientMessages.input(input));
            return true;
        } catch (RuntimeException e) {
            McTalking.LOGGER.warn("{} Could not deliver asynchronous tool result to the active session", logPrefix, e);
            return false;
        }
    }

    private static ArrayDeque<ProviderToolCall> extractProviderToolCalls(String message) {
        ArrayDeque<ProviderToolCall> calls = new ArrayDeque<>();
        try {
            var parsed = JsonParser.parseString(message);
            if (!parsed.isJsonObject()) return calls;
            var outer = parsed.getAsJsonObject();
            if (!outer.has("toolCall") || !outer.get("toolCall").isJsonObject()) return calls;
            var toolCall = outer.getAsJsonObject("toolCall");
            if (!toolCall.has("functionCalls") || !toolCall.get("functionCalls").isJsonArray()) return calls;
            for (var element : toolCall.getAsJsonArray("functionCalls")) {
                if (!element.isJsonObject()) continue;
                var function = element.getAsJsonObject();
                if (!function.has("name") || !function.get("name").isJsonPrimitive()) continue;
                String id = function.has("id") && function.get("id").isJsonPrimitive()
                        ? function.get("id").getAsString()
                        : "";
                calls.addLast(new ProviderToolCall(function.get("name").getAsString(), id));
            }
        } catch (RuntimeException ignored) {
            // The Gemini library remains authoritative for malformed provider-message handling.
        }
        return calls;
    }

    private record ProviderToolCall(String name, String id) {
    }

    @Override
    public void onQuotaExceeded() {
        McTalking.LOGGER.warn("{} Quota exceeded for Gemini API, please check your API key and usage limits.", logPrefix);
        QuotaTracker.reportQuotaExceeded(getModelName());
        transitionRecovery("quota exceeded", () -> recoveryController.quotaExceeded("quota exceeded"));
        onQuotaExceededEvent("Quota exceeded for Gemini API, please check your API key and usage limits.");
        fireOnCloseActions();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        try {
            super.onClose(code, reason, remote);
        } catch (Exception e) {
            McTalking.LOGGER.error("{} Error in GeminiLiveClient.onClose", logPrefix, e);
        }

        if (recoveryController.intentionalClose()) return;
        if (QuotaTracker.isQuotaExceeded(getModelName())
                || recoveryState() == ProviderRecoveryController.State.QUOTA_EXCEEDED) return;

        var selectedAi = McTalkingConfig.INSTANCE.instance().currentAiModel;
        if (VoiceSelectionService.isExplicitVoiceRejection(code, reason)) {
            VoiceSelectionService.noteRejected(selectedAi, selectedVoiceName, code, reason);
            McTalking.LOGGER.warn("{} Retrying setup with a fallback voice after explicit voice rejection", logPrefix);
            scheduleRecovery("explicit voice rejection");
            return;
        }

        ProviderRecoveryController.CloseDisposition disposition =
                ProviderRecoveryController.classifyClose(code, reason);

        if (disposition == ProviderRecoveryController.CloseDisposition.SESSION_TOKEN_INVALID) {
            McTalking.LOGGER.info("{} Session token invalidated; clearing resumable state before bounded reconnect", logPrefix);
            var mem = ((CitizenDataMemoryExtended) entity.getCitizenData()).mc_talking$getOrInitializeMemory();
            mem.setSessionToken("");
            int discardedAudio = stream.discardPendingAudio();
            if (discardedAudio > 0) {
                McTalking.LOGGER.info("{} Discarded {} stale queued audio chunks before replaying invalidated session",
                        logPrefix, discardedAudio);
            }
            if (lastPromptText != null) {
                synchronized (pendingSystemText) {
                    if (!pendingSystemText.contains(lastPromptText)) pendingSystemText.add(lastPromptText);
                }
            }
            scheduleRecovery("session token invalidated");
            return;
        }

        if (disposition == ProviderRecoveryController.CloseDisposition.NORMAL) {
            transitionRecovery("normal close", () -> recoveryController.closeNormal("provider close " + code + ": " + reason));
            McTalking.LOGGER.info("{} GeminiWsClient closed normally: {}", logPrefix, reason);
            fireOnCloseActions();
            return;
        }

        if (disposition == ProviderRecoveryController.CloseDisposition.TRANSIENT) {
            McTalking.LOGGER.warn("{} Transient provider close {}: {}; scheduling bounded recovery", logPrefix, code, reason);
            scheduleRecovery("provider close " + code + ": " + reason);
            return;
        }

        ProviderRecoveryController.TerminalReason terminalReason = switch (disposition) {
            case AUTHENTICATION_FAILURE -> ProviderRecoveryController.TerminalReason.AUTHENTICATION;
            case CONFIGURATION_FAILURE -> ProviderRecoveryController.TerminalReason.CONFIGURATION;
            case POLICY_FAILURE -> ProviderRecoveryController.TerminalReason.PROVIDER_POLICY;
            default -> ProviderRecoveryController.TerminalReason.PROVIDER_ERROR;
        };
        String detail = "provider close " + code + ": " + (reason == null ? "" : reason);
        transitionRecovery(detail, () -> recoveryController.terminal(terminalReason, detail));
        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.NONE);
        finishProviderTerminal(new RuntimeException(detail));
    }

    @Override
    public void onError(Exception ex) {
        McTalking.LOGGER.error("{} Error in GeminiWsClient", logPrefix, ex);
        ProviderRecoveryController.Diagnostic diagnostic = recoveryController.diagnostic();
        if (recoveryController.intentionalClose() || diagnostic.terminal()
                || QuotaTracker.isQuotaExceeded(getModelName())) return;
        scheduleRecovery("websocket error: " + ex.getClass().getSimpleName());
    }

    @Override
    public void addPromptAudio(short[] audio) {
        var input = new RealtimeInput();
        var byteAudio = vcApi.getAudioConverter().shortsToBytes(audio);
        input.audio = new RealtimeInput.Blob("audio/pcm;rate=48000", byteAudio);

        if (sentGeneratingStatus)
            onGenerationPaused();


        if (!isSessionReadyForInput()) {
            synchronized (pendingPrompt) {
                pendingPrompt.add(audio);
            }
            ensureConnectionForQueuedInput("addPromptAudio");
            return;
        }

        send(ClientMessages.input(input));
    }

    /**
     * Queues {@code text} to be sent to the API after the current AI turn finishes.
     * If the session is not yet ready the text is buffered in {@link #pendingSystemText}
     * and sent once setup completes.
     *
     * <p>The text is also saved to {@link #lastPromptText} so that if the session
     * token is later invalidated the prompt can be replayed on the new connection,
     * preventing system-controlled conversations from going silent.</p>
     *
     * @param text the text prompt to send after the current AI turn completes
     */
    public void addPromptTextAfterTalkingComplete(String text) {
        this.lastPromptText = text;

        if (sentGeneratingStatus)
            onGenerationPaused();

        if (!isSessionReadyForInput()) {
            synchronized (pendingSystemText) {
                pendingSystemText.add(text);
            }
            ensureConnectionForQueuedInput("addPromptTextAfterTalkingComplete");
            return;
        }

        pendingTextAfterTalking.add(text);
    }

    @Override
    public void connect() {
        if (recoveryController.diagnostic().terminal()) {
            McTalking.LOGGER.debug("{} Ignoring connect() after terminal provider state {}",
                    logPrefix, recoveryController.diagnostic().state());
            return;
        }
        hasMadeInitialConnection = true;
        transitionRecovery("connect()", () -> recoveryController.markConnecting("connect()"));
        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.CONNECTING);
        super.connect();
    }

    @Override
    public void reconnect() {
        scheduleRecovery("explicit reconnect");
    }

    public void promptAudioOpus(byte[] audio) {
        if (decoder == null) return;
        var raw = decoder.decode(audio);
        addPromptAudio(raw);
    }

    @Override
    public void close() {
        if (!closeStarted.compareAndSet(false, true)) return;
        synchronized (this) {
            transitionRecovery("close()", () -> recoveryController.closeIntentional("close()"));
            reconnectScheduled = false;
            if (reconnectFuture != null) {
                reconnectFuture.cancel(false);
                reconnectFuture = null;
            }
            if (gracefulEndFuture != null) {
                gracefulEndFuture.cancel(false);
                gracefulEndFuture = null;
            }
        }
        ADDON_TOOL_DISPATCHER.forgetSession(toolSessionId);
        AiStatusHelper.setAiStatusSynced(getEntity(), AiStatus.NONE);
        try {
            super.close();
        } finally {
            stream.close();
            fireOnCloseActions();
        }
    }

    /**
     * If set to true, avoids to send new status updates while the conversation is active, which can be used to reduce status update spam when the AI is generating multiple turns in a row.
     *
     * @return false, if new status updates should NOT be sent
     */
    public boolean sendStatusUpdates() {
        return true;
    }

    public static void shutdownExecutor() {
        if (RECONNECT_EXECUTOR == null) return;
        RECONNECT_EXECUTOR.shutdown();
        try {
            if (!RECONNECT_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                RECONNECT_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            RECONNECT_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
