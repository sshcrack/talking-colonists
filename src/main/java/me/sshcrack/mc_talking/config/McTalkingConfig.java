package me.sshcrack.mc_talking.config;

import me.sshcrack.mc_talking.manager.tools.AITools;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    public final ModConfigSpec.ConfigValue<Boolean> enableFunctionWorkaround;

    // Resource Management
    public final ModConfigSpec.ConfigValue<Integer> maxConcurrentAgents;
    public final ModConfigSpec.ConfigValue<Double> maxConversationDistance;
    public final ModConfigSpec.ConfigValue<ModalityModes> modality;
    public final ModConfigSpec.ConfigValue<List<? extends String>> disabledTools;

    public McTalkingConfig(ModConfigSpec.Builder builder) {

        // API Configuration
        geminiApiKey = builder
                .comment("API Configuration")
                .gameRestart()
                .comment("This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey")
                .define("gemini_key", "");

        currentAiModel = builder
                .gameRestart()
                .comment("What kind of AI model to use. Flash2.5 is more advanced, more expensive but has more voices as well. Flash2.5 burns the free tokens fast. Flash2.5 can only execute functions (for example dropping items, getting information about the colony) when Google Search is enabled.")
                .defineEnum("ai_model", AvailableAI.Flash2_5);

        enableFunctionWorkaround = builder
                .gameRestart()
                .comment("Enables the Google Search so Flash2.5 can execute functions. Google Search will ONLY be enabled for Flash2.5.")
                .define("function_workaround", true);

        // Language Configuration
        language = builder
                .comment("Language Configuration")
                .gameRestart()
                .comment("The language the AI should use to speak")
                .define("language", "en-US");

        // Interaction Configuration
        respondInGroups = builder
                .gameRestart()
                .comment("Interaction Configuration")
                .comment("Whether the citizens should respond if the player is in a group or not.")
                .define("respond_in_group", false);

        lookDurationTicks = builder
                .gameRestart()
                .comment("How long the player needs to look at an entity before activating (in ticks, 20 ticks = 1 second)")
                .define("look_duration_ticks", 20);

        lookToleranceMs = builder
                .gameRestart()
                .comment("Tolerance time in milliseconds when something walks between player and target")
                .define("look_tolerance_ms", 500);

        activationDistance = builder
                .gameRestart()
                .comment("Distance at which the player can talk to when looking at them the citizen")
                .define("activation_distance", 3.0);

        useTalkingDevice = builder
                .gameRestart()
                .comment("If true, citizens will only respond to the talking device item; if false, looking at them will work")
                .define("use_talking_device", true);

        // Resource Management
        maxConcurrentAgents = builder
                .gameRestart()
                .comment("Resource Management")
                .comment("Maximum number of AI agents that can be activated at once")
                .define("max_concurrent_agents", 3, e -> e == null || (int) e > 0);

        maxConversationDistance = builder
                .gameRestart()
                .comment("Maximum distance the player can be from a citizen before the conversation is ended")
                .define("max_conversation_distance", 8.0);

        modality = builder
                .gameRestart()
                .comment("The modality of the AI. If true, the AI will use text and audio, if false, it will only use text. Gemini Live 2.5 doesn't support text only output.")
                .defineEnum("ai_modality", ModalityModes.AUDIO);


        AtomicInteger currIndex = new AtomicInteger();
        disabledTools = builder
                .gameRestart()
                .comment("List of enabled tools for the AI. These tools can be used by the AI to perform actions.")
                .defineList("disabled_tools", Collections::emptyList, () -> {
                    var l = AITools.getRegisteredFunctionNames();
                    return l.get((currIndex.incrementAndGet() - 1) % l.size());
                }, e -> {
                    if(e instanceof String str) {
                        return AITools.getRegisteredFunctionNames().contains(str);
                    }

                    return false;
                });
    }

    static {
        Pair<McTalkingConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(McTalkingConfig::new);

        //Store the resulting values
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }
}
