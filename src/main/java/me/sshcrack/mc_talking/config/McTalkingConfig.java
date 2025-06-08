package me.sshcrack.mc_talking.config;

import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.manager.AvailableAI;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration class for the McTalking mod.
 * Handles loading and managing configuration options.
 */
@EventBusSubscriber(modid = McTalking.MODID, bus = EventBusSubscriber.Bus.MOD)
public class McTalkingConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // API Configuration
    public static final ModConfigSpec.ConfigValue<String> GEMINI_API_KEY = BUILDER
            .comment("API Configuration")
            .worldRestart()
            .comment("This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey")
            .define("gemini_key", "");

    public static final ModConfigSpec.ConfigValue<AvailableAI> CURRENT_AI_MODEL = BUILDER
            .comment("What kind of AI model to use. Flash2.5 is more advanced, more expensive but has more voices as well. Flash2.5 burns the free tokens fast. Flash2.5 has an issue for calling functions right now, so colonists are not able to leave the colony or get information about a citizen etc.")
            .defineEnum("ai_model", AvailableAI.Flash2_0);

    // Language Configuration
    public static final ModConfigSpec.ConfigValue<String> LANGUAGE = BUILDER
            .comment("Language Configuration")
            .worldRestart()
            .comment("The language the AI should use to speak")
            .define("language", "en-US");

    // Interaction Configuration
    public static final ModConfigSpec.ConfigValue<Boolean> RESPOND_IN_GROUPS = BUILDER
            .comment("Interaction Configuration")
            .comment("Whether the citizens should respond if the player is in a group or not.")
            .define("respond_in_group", false);

    public static final ModConfigSpec.ConfigValue<Integer> LOOK_DURATION_TICKS = BUILDER
            .comment("How long the player needs to look at an entity before activating (in ticks, 20 ticks = 1 second)")
            .define("look_duration_ticks", 20);

    public static final ModConfigSpec.ConfigValue<Integer> LOOK_TOLERANCE_MS = BUILDER
            .comment("Tolerance time in milliseconds when something walks between player and target")
            .define("look_tolerance_ms", 500);

    public static final ModConfigSpec.ConfigValue<Double> ACTIVATION_DISTANCE = BUILDER
            .comment("Distance at which the player can talk to when looking at them the citizen")
            .define("activation_distance", 3.0);

    public static final ModConfigSpec.ConfigValue<Boolean> USE_TALKING_DEVICE = BUILDER
            .comment("If true, citizens will only respond to the talking device item; if false, looking at them will work")
            .define("use_talking_device", true);

    // Resource Management
    public static final ModConfigSpec.ConfigValue<Integer> MAX_CONCURRENT_AGENTS = BUILDER
            .comment("Resource Management")
            .comment("Maximum number of AI agents that can be activated at once")
            .define("max_concurrent_agents", 3, e -> e == null || (int) e > 0);

    public static final ModConfigSpec.ConfigValue<Double> MAX_CONVERSATION_DISTANCE = BUILDER
            .comment("Maximum distance the player can be from a citizen before the conversation is ended")
            .define("max_conversation_distance", 8.0);

    public static final ModConfigSpec.ConfigValue<Boolean> TEXT_REPLY = BUILDER
            .comment("If true, the AI will respond in chat with text messages instead of voice replies. Only applies to Gemini Live 2.0")
            .define("text_reply", true);

    // Config specification
    public static final ModConfigSpec SPEC = BUILDER
            .build();

    // Runtime configuration values
    public static String geminiApiKey;
    public static AvailableAI currentAIModel;
    public static String language;
    public static int maxConcurrentAgents;
    public static boolean respondInGroups;
    public static boolean textReply;
    public static double activationDistance;
    public static int lookDurationTicks;
    public static int lookToleranceMs;
    public static boolean useTalkingDevice;
    public static double maxConversationDistance;

    /**
     * Event handler for when the configuration is loaded or reloaded.
     * Applies configuration values to runtime variables.
     *
     * @param event The configuration event
     */
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        geminiApiKey = GEMINI_API_KEY.get();
        textReply = TEXT_REPLY.get();
        respondInGroups = RESPOND_IN_GROUPS.get();
        activationDistance = ACTIVATION_DISTANCE.get();
        lookDurationTicks = LOOK_DURATION_TICKS.get();
        lookToleranceMs = LOOK_TOLERANCE_MS.get();
        maxConcurrentAgents = MAX_CONCURRENT_AGENTS.get();
        language = LANGUAGE.get();
        useTalkingDevice = USE_TALKING_DEVICE.get();
        maxConversationDistance = MAX_CONVERSATION_DISTANCE.get();
        currentAIModel = CURRENT_AI_MODEL.get();
        GeminiWsClient.quotaExceeded = false;

        McTalking.LOGGER.info("McTalking configuration loaded");

        // Log warning if API key is not set
        if (geminiApiKey.isEmpty()) {
            McTalking.LOGGER.warn("Gemini API key not set. McTalking will be disabled until a key is provided.");
        }
    }
}
