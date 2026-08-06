package me.sshcrack.mc_talking.session;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.provider.*;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.manager.audio.CitizenEntityAudioProvider;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Creates {@link CitizenGameSession} instances from the configured
 * {@link ProviderSelection}, resolving providers through {@link AiProviderRegistry}.
 */
public class SessionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger("SessionFactory");

    private final ProviderSelection selection;

    public SessionFactory(ProviderSelection selection) {
        this.selection = selection;
    }

    /**
     * Creates a {@link CitizenGameSession} for a player conversation.
     * Delegates to {@link #createSession} but also assigns the player.
     */
    public Optional<CitizenGameSession> createPlayerSession(AbstractEntityCitizen citizen, ServerPlayer player) {
        return createSession(citizen, new CitizenEntityAudioProvider(citizen, null))
                .map(session -> new CitizenGameSession(session.liveSession, citizen, session.stream, player));
    }

    /**
     * Creates a {@link CitizenGameSession} wrapping a {@link LiveSession}
     * resolved from the registry.
     *
     * @return the session, or empty if no provider is available for the selected configuration
     */
    public Optional<CitizenGameSession> createSession(AbstractEntityCitizen citizen, AudioProvider audioProvider) {
        String systemPrompt = generateSystemPrompt(citizen);

        if (selection.hasLiveBundle()) {
            return createBundledSession(citizen, audioProvider, systemPrompt);
        } else if (selection.hasComposable()) {
            return createComposableSession(citizen, audioProvider, systemPrompt);
        }

        LOGGER.warn("No provider configured for citizen {} - selection has neither live bundle nor composable providers", citizen.getUUID());
        return Optional.empty();
    }

    private Optional<CitizenGameSession> createBundledSession(AbstractEntityCitizen citizen, AudioProvider audioProvider, String systemPrompt) {
        Optional<LiveSessionProvider> bundle = AiProviderRegistry.getProvider(
                selection.liveBundleProviderId(), LiveSessionProvider.class);

        if (bundle.isEmpty()) {
            LOGGER.warn("Live bundle provider '{}' not found in registry", selection.liveBundleProviderId());
            return Optional.empty();
        }

        var configBuilder = LiveSessionConfig.builder()
                .systemPrompt(systemPrompt)
                .language(getLanguage())
                .voice(getVoice(citizen));

        addTools(configBuilder);

        LiveSession liveSession = bundle.get().createSession(configBuilder.build());
        var stream = new me.sshcrack.mc_talking.manager.GeminiStream(audioProvider.createChannel());
        return Optional.of(new CitizenGameSession(liveSession, citizen, stream));
    }

    private Optional<CitizenGameSession> createComposableSession(AbstractEntityCitizen citizen, AudioProvider audioProvider, String systemPrompt) {
        Optional<SttProvider> stt = AiProviderRegistry.getProvider(selection.sttProviderId(), SttProvider.class);
        Optional<LlmProvider> llm = AiProviderRegistry.getProvider(selection.llmProviderId(), LlmProvider.class);
        Optional<TtsProvider> tts = AiProviderRegistry.getProvider(selection.ttsProviderId(), TtsProvider.class);

        if (stt.isEmpty() || llm.isEmpty() || tts.isEmpty()) {
            LOGGER.warn("Missing providers for composable session: stt={}, llm={}, tts={}",
                    stt.isPresent(), llm.isPresent(), tts.isPresent());
            return Optional.empty();
        }

        var configBuilder = LiveSessionConfig.builder()
                .systemPrompt(systemPrompt)
                .language(getLanguage())
                .voice(getVoice(citizen));

        addTools(configBuilder);

        var session = new LocalComposableSession(stt.get(), llm.get(), tts.get(), configBuilder.build());
        var stream = new me.sshcrack.mc_talking.manager.GeminiStream(audioProvider.createChannel());
        return Optional.of(new CitizenGameSession(session, citizen, stream));
    }

    private String generateSystemPrompt(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        if (data == null) return "You are a MineColonies citizen.";

        var view = me.sshcrack.mc_talking.manager.CitizenPromptViewFactory.create(
                data, new java.util.HashMap<>(), null);
        return me.sshcrack.mc_talking.api.prompt.CitizenPromptService.generateSystemControlledRoleplayPrompt(view);
    }

    private void addTools(LiveSessionConfig.Builder configBuilder) {
        var tools = me.sshcrack.mc_talking.manager.tools.AITools.getEnabledTools();
        for (var tool : tools) {
            if (tool.functionDeclarations != null && !tool.functionDeclarations.isEmpty()) {
                for (var decl : tool.functionDeclarations) {
                    configBuilder.addTool(new ToolDefinition(
                            decl.name,
                            decl.description,
                            java.util.Map.of()
                    ));
                }
            }
        }
    }

    private String getLanguage() {
        return me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().language;
    }

    @Nullable
    private String getVoice(AbstractEntityCitizen citizen) {
        return me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().currentAiModel.getRandomVoice(
                citizen.getUUID(),
                citizen.getCitizenData() != null && citizen.getCitizenData().isFemale()
        );
    }
}
