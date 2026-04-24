package me.sshcrack.mc_talking.config;

import me.sshcrack.mc_talking.manager.tools.AITools;
/*? if forge {*/
/*import net.minecraftforge.common.ForgeConfigSpec;
*//*?}*/
/*? if neoforge {*/
import net.neoforged.neoforge.common.ModConfigSpec;
/*?}*/
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
    /*? if forge {*/
    /*public static final ForgeConfigSpec CONFIG_SPEC;
    *//*?}*/
    /*? if neoforge {*/
    public static final ModConfigSpec CONFIG_SPEC;
    /*?}*/

    // API Configuration
    /*? if forge {*/
    /*public final ForgeConfigSpec.ConfigValue<String> geminiApiKey;
    public final ForgeConfigSpec.ConfigValue<AvailableAI> currentAiModel;
    *//*?}*/
    /*? if neoforge {*/
    public final ModConfigSpec.ConfigValue<String> geminiApiKey;
    public final ModConfigSpec.ConfigValue<AvailableAI> currentAiModel;
    /*?}*/

    // Language Configuration
    /*? if forge {*/
    /*public final ForgeConfigSpec.ConfigValue<String> language;
    *//*?}*/
    /*? if neoforge {*/
    public final ModConfigSpec.ConfigValue<String> language;
    /*?}*/

    // Interaction Configuration
    /*? if forge {*/
    /*public final ForgeConfigSpec.ConfigValue<Boolean> respondInGroups;
    public final ForgeConfigSpec.ConfigValue<Integer> lookDurationTicks;
    public final ForgeConfigSpec.ConfigValue<Integer> lookToleranceMs;
    public final ForgeConfigSpec.ConfigValue<Double> activationDistance;
    public final ForgeConfigSpec.ConfigValue<Boolean> useTalkingDevice;
    public final ForgeConfigSpec.ConfigValue<Boolean> enableFunctionWorkaround;
    public final ForgeConfigSpec.ConfigValue<Boolean> sendErrorsToPlayers;
    *//*?}*/
    /*? if neoforge {*/
    public final ModConfigSpec.ConfigValue<Boolean> respondInGroups;
    public final ModConfigSpec.ConfigValue<Integer> lookDurationTicks;
    public final ModConfigSpec.ConfigValue<Integer> lookToleranceMs;
    public final ModConfigSpec.ConfigValue<Double> activationDistance;
    public final ModConfigSpec.ConfigValue<Boolean> useTalkingDevice;
    public final ModConfigSpec.ConfigValue<Boolean> enableFunctionWorkaround;
    public final ModConfigSpec.ConfigValue<Boolean> sendErrorsToPlayers;
    /*?}*/

    // Resource Management
    /*? if forge {*/
    /*public final ForgeConfigSpec.ConfigValue<Integer> maxConcurrentAgents;
    public final ForgeConfigSpec.ConfigValue<Double> maxConversationDistance;
    public final ForgeConfigSpec.ConfigValue<ModalityModes> modality;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> disabledTools;
    *//*?}*/
    /*? if neoforge {*/
    public final ModConfigSpec.ConfigValue<Integer> maxConcurrentAgents;
    public final ModConfigSpec.ConfigValue<Double> maxConversationDistance;
    public final ModConfigSpec.ConfigValue<ModalityModes> modality;
    public final ModConfigSpec.ConfigValue<List<? extends String>> disabledTools;
    /*?}*/

    /*? if forge {*/
    /*public McTalkingConfig(ForgeConfigSpec.Builder builder) {
    *//*?}*/
    /*? if neoforge {*/
    public McTalkingConfig(ModConfigSpec.Builder builder) {
    /*?}*/

        // API Configuration
        geminiApiKey = builder
                .comment("API Configuration")
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey")
                .define("gemini_key", "");

        currentAiModel = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("What kind of AI model to use. Right now, this is the only one Google offers")
                .defineEnum("ai_model", AvailableAI.Flash3);
        enableFunctionWorkaround = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("Enables the Google Search so Flash2.5 can execute functions. Google Search will ONLY be enabled for Flash2.5.")
                .define("function_workaround", true);

        // Language Configuration
        language = builder
                .comment("Language Configuration")
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("The language the AI should use to speak")
                .define("language", "en-US");

        // Interaction Configuration
        respondInGroups = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("Interaction Configuration")
                .comment("Whether the citizens should respond if the player is in a group or not.")
                .define("respond_in_group", false);

        lookDurationTicks = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("How long the player needs to look at an entity before activating (in ticks, 20 ticks = 1 second)")
                .define("look_duration_ticks", 20);

        lookToleranceMs = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("Tolerance time in milliseconds when something walks between player and target")
                .define("look_tolerance_ms", 500);

        activationDistance = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("Distance at which the player can talk to when looking at them the citizen")
                .define("activation_distance", 3.0);

        useTalkingDevice = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("If true, citizens will only respond to the talking device item; if false, looking at them will work")
                .define("use_talking_device", true);

        // Resource Management
        maxConcurrentAgents = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("Resource Management")
                .comment("Maximum number of AI agents that can be activated at once (for free tier Flash2.0 this is limited to 3, for Flash2.5 to 1)")
                .define("max_concurrent_agents", 3, e -> e == null || (int) e > 0);

        maxConversationDistance = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("Maximum distance the player can be from a citizen before the conversation is ended")
                .define("max_conversation_distance", 8.0);

        modality = builder
                /*? if forge {*/
                /*.worldRestart()
                *//*?}*/
                /*? if neoforge {*/
                .gameRestart()
                /*?}*/
                .comment("In which format the AI should respond. This can be text, audio or both.")
                .defineEnum("ai_modality", ModalityModes.AUDIO);


        /*? if forge {*/
        /*disabledTools = builder
                .worldRestart()
                .comment("List of enabled tools for the AI. These tools can be used by the AI to perform actions. Use the /list_tools command to see a list of the available tools.")
                .defineList("disabled_tools", Collections::emptyList, e -> {
                    if (e instanceof String str) {
                        return AITools.getRegisteredFunctionNames().contains(str);
                    }

                    return false;
                });
        *//*?}*/
        /*? if neoforge {*/
        AtomicInteger currIndex = new AtomicInteger();
        disabledTools = builder
                .gameRestart()
                .comment("List of enabled tools for the AI. These tools can be used by the AI to perform actions. Use the /list_tools command to see a list of the available tools.")
                .defineList("disabled_tools", Collections::emptyList, () -> {
                    var l = AITools.getRegisteredFunctionNames();
                    return l.get((currIndex.incrementAndGet() - 1) % l.size());
                }, e -> {
                    if(e instanceof String str) {
                        return AITools.getRegisteredFunctionNames().contains(str);
                    }

                    return false;
                });
        /*?}*/

        sendErrorsToPlayers = builder
                .comment("If true, errors will be sent to players that have OP permissions. If false, errors will only be logged to the console.")
                .define("send_errors_to_players", true);
    }

    static {
        /*? if forge {*/
        /*Pair<McTalkingConfig, ForgeConfigSpec> pair =
                new ForgeConfigSpec.Builder().configure(McTalkingConfig::new);
        *//*?}*/
        /*? if neoforge {*/
        Pair<McTalkingConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(McTalkingConfig::new);
        /*?}*/

        //Store the resulting values
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }
}
