package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.service.ContextService;
import me.sshcrack.mc_talking.api.service.ConversationRuleService;
import me.sshcrack.mc_talking.api.service.ConversationService;
import me.sshcrack.mc_talking.api.service.MemoryService;
import me.sshcrack.mc_talking.api.service.PregenerationService;
import me.sshcrack.mc_talking.api.service.PromptService;
import me.sshcrack.mc_talking.api.service.ToolService;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/** Small bootstrap aggregate for the focused addon service implementations. */
public final class TalkingColonistsApiBackend implements TalkingColonistsApi.Services {
    public static final TalkingColonistsApiBackend INSTANCE = new TalkingColonistsApiBackend();

    /**
     * Single source of truth for {@link #supports(ApiFeature)}. Every {@link ApiFeature} constant
     * is added the moment its roadmap task starts landing, long before this set gains the matching
     * entry; a task only adds itself here once its feature is fully implemented and tested. Empty
     * today: no Track A task has landed yet.
     */
    private static final Set<ApiFeature> SUPPORTED_FEATURES = EnumSet.of(ApiFeature.BROADCAST_PUBLISHING);

    private final PromptService prompts = new PromptServiceBackend();
    private final ConversationRuleService conversationRules = new ConversationRuleServiceBackend();
    private final PregenerationService pregeneration = new PregenerationServiceBackend();
    private final ToolService tools = new ToolServiceBackend();
    private final ContextService context = new ContextServiceBackend();
    private final ConversationServiceBackend conversations = new ConversationServiceBackend();
    private final MemoryService memory = new MemoryServiceBackend();

    private TalkingColonistsApiBackend() {
    }

    @Override public int apiMajorVersion() { return TalkingColonistsApi.API_MAJOR_VERSION; }
    @Override public int apiMinorVersion() { return TalkingColonistsApi.API_MINOR_VERSION; }
    @Override public boolean supports(@NotNull ApiFeature feature) { return SUPPORTED_FEATURES.contains(feature); }
    @Override public @NotNull PromptService prompts() { return prompts; }
    @Override public @NotNull ConversationRuleService conversationRules() { return conversationRules; }
    @Override public @NotNull PregenerationService pregeneration() { return pregeneration; }
    @Override public @NotNull ToolService tools() { return tools; }
    @Override public @NotNull ContextService context() { return context; }
    @Override public @NotNull ConversationService conversations() { return conversations; }
    @Override public @NotNull MemoryService memory() { return memory; }

    public static void onServerStopping(@NotNull MinecraftServer server) {
        ConversationServiceBackend.onServerStopping(server);
    }
}
