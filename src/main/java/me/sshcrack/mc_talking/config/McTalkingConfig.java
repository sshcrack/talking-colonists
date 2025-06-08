package me.sshcrack.mc_talking.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration class for the McTalking mod.
 * Handles loading and managing configuration options.
 */
public class McTalkingConfig {
    public static final McTalkingConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    // API Configuration
    public final ModConfigSpec.ConfigValue<String> geminiApiKey;
    public final ModConfigSpec.ConfigValue<AvailableAI> currentAiModel;

    // Language Configuration
    public final ModConfigSpec.ConfigValue<String> language;

    // Interaction Configuration
    public final ModConfigSpec.ConfigValue<Boolean> respondInGroups;
    public final ModConfigSpec.ConfigValue<Integer> lookDurationTicks;
    public final ModConfigSpec.ConfigValue<Integer> lookToleranceMs;
    public final ModConfigSpec.ConfigValue<Double> activationDistance;
    public final ModConfigSpec.ConfigValue<Boolean> useTalkingDevice;

    // Resource Management
    public final ModConfigSpec.ConfigValue<Integer> maxConcurrentAgents;
    public final ModConfigSpec.ConfigValue<Double> maxConversationDistance;
    public final ModConfigSpec.ConfigValue<ModalityModes> modality;

    public McTalkingConfig(ModConfigSpec.Builder builder) {

        // API Configuration
        geminiApiKey = builder
                .comment("API Configuration")
                .worldRestart()
                .comment("This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey")
                .define("gemini_key", "");

        currentAiModel = builder
                .comment("What kind of AI model to use. Flash2.5 is more advanced, more expensive but has more voices as well. Flash2.5 burns the free tokens fast. Flash2.5 has an issue for calling functions right now, so colonists are not able to leave the colony or get information about a citizen etc.")
                .defineEnum("ai_model", AvailableAI.Flash2_0);

        // Language Configuration
        language = builder
                .comment("Language Configuration")
                .worldRestart()
                .comment("The language the AI should use to speak")
                .define("language", "en-US");

        // Interaction Configuration
        respondInGroups = builder
                .comment("Interaction Configuration")
                .comment("Whether the citizens should respond if the player is in a group or not.")
                .define("respond_in_group", false);

        lookDurationTicks = builder
                .comment("How long the player needs to look at an entity before activating (in ticks, 20 ticks = 1 second)")
                .define("look_duration_ticks", 20);

        lookToleranceMs = builder
                .comment("Tolerance time in milliseconds when something walks between player and target")
                .define("look_tolerance_ms", 500);

        activationDistance = builder
                .comment("Distance at which the player can talk to when looking at them the citizen")
                .define("activation_distance", 3.0);

        useTalkingDevice = builder
                .comment("If true, citizens will only respond to the talking device item; if false, looking at them will work")
                .define("use_talking_device", true);

        // Resource Management
        maxConcurrentAgents = builder
                .comment("Resource Management")
                .comment("Maximum number of AI agents that can be activated at once")
                .define("max_concurrent_agents", 3, e -> e == null || (int) e > 0);

        maxConversationDistance = builder
                .comment("Maximum distance the player can be from a citizen before the conversation is ended")
                .define("max_conversation_distance", 8.0);

        modality = builder
                .comment("The modality of the AI. If true, the AI will use text and audio, if false, it will only use text. Gemini Live 2.5 doesn't support text only output.")
                .defineEnum("ai_modality", ModalityModes.AUDIO);
    }

    static {
        Pair<McTalkingConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(McTalkingConfig::new);

        //Store the resulting values
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }
}
