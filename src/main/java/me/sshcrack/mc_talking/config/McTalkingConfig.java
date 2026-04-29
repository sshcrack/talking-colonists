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
import java.util.function.Supplier;

/**
 * Configuration class for the McTalking mod.
 * Handles loading and managing configuration options.
 */
public class McTalkingConfig {
    public static final McTalkingConfig CONFIG;
    public static final String FLASH_MODEL = "gemini-flash-latest";
    public static final String TTS_MODEL = "gemini-3.1-flash-tts-preview";

    /*? if forge {*/
    /*public static final ForgeConfigSpec CONFIG_SPEC;
    *//*?}*/
    /*? if neoforge {*/
    public static final ModConfigSpec CONFIG_SPEC;
    /*?}*/

    // API Configuration
    public final Supplier<String> geminiApiKey;
    public final Supplier<AvailableAI> currentAiModel;

    // Language Configuration
    public final Supplier<String> language;

    // Interaction Configuration
    public final Supplier<Boolean> respondInGroups;
    public final Supplier<Integer> lookDurationTicks;
    public final Supplier<Integer> lookToleranceMs;
    public final Supplier<Double> activationDistance;
    public final Supplier<Boolean> useTalkingDevice;
    public final Supplier<Boolean> sendErrorsToPlayers;

    // Resource Management
    public final Supplier<Integer> maxConcurrentAgents;
    public final Supplier<Double> maxConversationDistance;
    public final Supplier<ModalityModes> modality;
    public final Supplier<List<? extends String>> disabledTools;

    /*? if forge {*/
    /*public McTalkingConfig(ForgeConfigSpec.Builder builder) {
        *//*?}*/
        /*? if neoforge {*/
        public McTalkingConfig(ModConfigSpec.Builder builder) {
         /*?}*/

        // API Configuration
        geminiApiKey = requireRestart(builder)
                .comment("API Configuration")
                .comment("This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey")
                .define("gemini_key", "");

        currentAiModel = requireRestart(builder)
                .comment("What kind of AI model to use. Right now, this is the only one Google offers")
                .defineEnum("ai_model", AvailableAI.Flash3);

        // Language Configuration
        language = requireRestart(builder)
                .comment("Language Configuration")
                .comment("The language the AI should use to speak")
                .define("language", "en-US");

        // Interaction Configuration
        respondInGroups = requireRestart(builder)
                .comment("Interaction Configuration")
                .comment("Whether the citizens should respond if the player is in a group or not.")
                .define("respond_in_group", false);

        lookDurationTicks = requireRestart(builder)
                .comment("How long the player needs to look at an entity before activating (in ticks, 20 ticks = 1 second)")
                .define("look_duration_ticks", 20);

        lookToleranceMs = requireRestart(builder)
                .comment("Tolerance time in milliseconds when something walks between player and target")
                .define("look_tolerance_ms", 500);

        activationDistance = requireRestart(builder)
                .comment("Distance at which the player can talk to when looking at them the citizen")
                .define("activation_distance", 3.0);

        useTalkingDevice = requireRestart(builder)
                .comment("If true, citizens will only respond to the talking device item; if false, looking at them will work")
                .define("use_talking_device", true);

        // Resource Management
        maxConcurrentAgents = requireRestart(builder)
                .comment("Resource Management")
                .comment("Maximum number of AI agents that can be activated at once (for free tier Flash2.0 this is limited to 3, for Flash2.5 to 1)")
                .define("max_concurrent_agents", 3, e -> e == null || (int) e > 0);

        maxConversationDistance = requireRestart(builder)
                .comment("Maximum distance the player can be from a citizen before the conversation is ended")
                .define("max_conversation_distance", 8.0);

        modality = requireRestart(builder)
                .comment("In which format the AI should respond. This can be text, audio or both.")
                .defineEnum("ai_modality", ModalityModes.AUDIO);

        var disabledToolsBuilder = requireRestart(builder)
                .comment("List of enabled tools for the AI. These tools can be used by the AI to perform actions. Use the /list_tools command to see a list of the available tools.");


        /*? if forge {*/
        /*disabledTools = disabledToolsBuilder
                .defineList("disabled_tools", Collections::emptyList, e -> {
                    if (e instanceof String str) {
                        return AITools.getRegisteredFunctionNames().contains(str);
                    }

                    return false;
                });
        *//*?}*/
        /*? if neoforge {*/
		AtomicInteger currIndex = new AtomicInteger();
		disabledTools = disabledToolsBuilder
				.defineList("disabled_tools", Collections::emptyList, () -> {
					var l = AITools.getRegisteredFunctionNames();
					return l.get((currIndex.incrementAndGet() - 1) % l.size());
				}, e -> {
					if (e instanceof String str) {
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

    /*? if neoforge {*/
	private static ModConfigSpec.Builder requireRestart(ModConfigSpec.Builder builder) {
		return builder.gameRestart();
	}
	/*?}*/

    /*? if forge {*/
    /*private static ForgeConfigSpec.Builder requireRestart(ForgeConfigSpec.Builder builder) {
        return builder.worldRestart();
    }
    *//*?}*/
}
