package me.sshcrack.mc_talking.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration class for the McTalking mod.
 * Handles loading and managing configuration options.
 */
public class McTalkingConfig {
    public static final McTalkingConfig CONFIG;
    public static final ForgeConfigSpec CONFIG_SPEC;

    // API Configuration
    public final ForgeConfigSpec.ConfigValue<String> geminiApiKey;
    public final ForgeConfigSpec.ConfigValue<AvailableAI> currentAiModel;

    // Language Configuration
    public final ForgeConfigSpec.ConfigValue<String> language;

    // Interaction Configuration
    public final ForgeConfigSpec.ConfigValue<Boolean> respondInGroups;
    public final ForgeConfigSpec.ConfigValue<Integer> lookDurationTicks;
    public final ForgeConfigSpec.ConfigValue<Integer> lookToleranceMs;
    public final ForgeConfigSpec.ConfigValue<Double> activationDistance;
    public final ForgeConfigSpec.ConfigValue<Boolean> useTalkingDevice;
    public final ForgeConfigSpec.ConfigValue<Boolean> enableFunctionWorkaround;

    // Resource Management
    public final ForgeConfigSpec.ConfigValue<Integer> maxConcurrentAgents;
    public final ForgeConfigSpec.ConfigValue<Double> maxConversationDistance;
    public final ForgeConfigSpec.ConfigValue<ModalityModes> modality;    public McTalkingConfig(ForgeConfigSpec.Builder builder) {

        // API Configuration
        geminiApiKey = builder
                .comment("API Configuration")
                .worldRestart()
                .comment("This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey")
                .define("gemini_key", "");

        currentAiModel = builder
                .worldRestart()
                .comment("What kind of AI model to use. Flash2.5 is more advanced, more expensive but has more voices as well. Flash2.5 burns the free tokens fast. Flash2.5 can only execute functions (for example dropping items, getting information about the colony) when Google Search is enabled.")
                .defineEnum("ai_model", AvailableAI.Flash2_5);        enableFunctionWorkaround = builder
                .worldRestart()
                .comment("Enables the Google Search so Flash2.5 can execute functions. Google Search will ONLY be enabled for Flash2.5.")
                .define("function_workaround", true);

        // Language Configuration
        language = builder
                .comment("Language Configuration")
                .worldRestart()
                .comment("The language the AI should use to speak")
                .define("language", "en-US");

        // Interaction Configuration
        respondInGroups = builder
                .worldRestart()
                .comment("Interaction Configuration")
                .comment("Whether the citizens should respond if the player is in a group or not.")
                .define("respond_in_group", false);

        lookDurationTicks = builder
                .worldRestart()
                .comment("How long the player needs to look at an entity before activating (in ticks, 20 ticks = 1 second)")
                .define("look_duration_ticks", 20);

        lookToleranceMs = builder
                .worldRestart()
                .comment("Tolerance time in milliseconds when something walks between player and target")
                .define("look_tolerance_ms", 500);

        activationDistance = builder
                .worldRestart()
                .comment("Distance at which the player can talk to when looking at them the citizen")
                .define("activation_distance", 3.0);

        useTalkingDevice = builder
                .worldRestart()
                .comment("If true, citizens will only respond to the talking device item; if false, looking at them will work")
                .define("use_talking_device", true);

        // Resource Management
        maxConcurrentAgents = builder
                .worldRestart()
                .comment("Resource Management")
                .comment("Maximum number of AI agents that can be activated at once")
                .define("max_concurrent_agents", 3, e -> e == null || (int) e > 0);

        maxConversationDistance = builder
                .worldRestart()
                .comment("Maximum distance the player can be from a citizen before the conversation is ended")
                .define("max_conversation_distance", 8.0);        modality = builder
                .worldRestart()
                .comment("The modality of the AI. If true, the AI will use text and audio, if false, it will only use text. Gemini Live 2.5 doesn't support text only output.")
                .defineEnum("ai_modality", ModalityModes.AUDIO);
    }    static {
        Pair<McTalkingConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(McTalkingConfig::new);

        //Store the resulting values
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }
}
