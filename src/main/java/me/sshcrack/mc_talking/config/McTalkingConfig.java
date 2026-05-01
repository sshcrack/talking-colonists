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
    public static final String FLASH_MODEL = "gemini-flash-lite-latest";
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
    public final Supplier<Boolean> sendErrorsToPlayers;

    // Resource Management
    public final Supplier<Integer> maxConcurrentAgents;
    public final Supplier<Double> maxConversationDistance;
    public final Supplier<ModalityModes> modality;
    public final Supplier<List<? extends String>> disabledTools;

    // Citizen - Citizen Interaction
    public final Supplier<Boolean> enableCitizenMemory;
    public final Supplier<Boolean> enableCitizenToCitizenConversation;

    // Citizen-to-Citizen conversation mode
    public final Supplier<ConversationMode> conversationMode;

    // Random citizen-to-citizen conversations
    public final Supplier<Boolean> enableRandomConversations;
    public final Supplier<Double> randomConversationChance;
    public final Supplier<Integer> randomConversationCheckIntervalTicks;

    // Proximity Mumbling
    public final Supplier<Double> mumblingChance;
    public final Supplier<Double> mumblingDetectionRange;
    public final Supplier<Integer> mumblingCheckIntervalTicks;

    // Per-citizen automatic-session cooldown
    public final Supplier<Integer> citizenCooldownSeconds;

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


        // Citizen - Citizen Interaction (Conversations between them)

        enableCitizenMemory = requireRestart(builder)
                .comment("If true, citizens will remember previous conversations that happened between them. Not recommended for free tier")
                .define("enable_citizen_memory", false);

        enableCitizenToCitizenConversation = requireRestart(builder)
                .comment("If true, citizens will be able to start conversations with each other without player involvement. Only recommended for paid tiers, as there are only at max 10 conversations per day available for the free tier")
                .define("enable_citizen_to_citizen_conversation", false);

        conversationMode = requireRestart(builder)
                .comment("How citizen-to-citizen conversations are generated.")
                .comment("LIVE_WEBSOCKETS (default/cheaper): Two Gemini Live sessions feed audio to each other in real time - no Flash or TTS call needed.")
                .comment("FLASH_TTS (higher quality): Flash generates a script, then Gemini TTS renders multi-speaker audio.")
                .defineEnum("conversation_mode", ConversationMode.LIVE_WEBSOCKETS);

        // Random citizen-to-citizen conversations
        enableRandomConversations = requireRestart(builder)
                .comment("Random Citizen Conversations")
                .comment("If true, citizens will randomly start conversations with each other based on the chance below. Requires enable_citizen_to_citizen_conversation to be true.")
                .define("enable_random_conversations", true);

        randomConversationChance = builder
                .comment("Chance (0.0-1.0) that a pair of nearby citizens start a random conversation per check interval")
                .define("random_conversation_chance", 0.05);

        randomConversationCheckIntervalTicks = builder
                .comment("How often (in server ticks) to check for random citizen conversations. 20 ticks = 1 second")
                .define("random_conversation_check_interval_ticks", 400, e -> e == null || (int) e > 0);

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

        // Proximity Mumbling
        mumblingChance = builder
                .comment("Proximity Mumbling")
                .comment("Chance (0.0-1.0) that a nearby citizen starts mumbling to themselves per check interval")
                .define("mumbling_chance", 0.05);

        mumblingDetectionRange = builder
                .comment("Distance in blocks within which a citizen can be triggered to mumble when a player is nearby")
                .define("mumbling_detection_range", 10.0);

        mumblingCheckIntervalTicks = builder
                .comment("How often (in server ticks) to check for citizens to trigger mumbling near players. 20 ticks = 1 second")
                .define("mumbling_check_interval_ticks", 200, e -> e == null || (int) e > 0);

        // Per-citizen cooldown
        citizenCooldownSeconds = builder
                .comment("Per-Citizen Cooldown")
                .comment("Minimum number of seconds that must pass after a citizen's automatic session (mumble or citizen-to-citizen) ends before they can be selected for another one. Set to 0 to disable.")
                .define("citizen_cooldown_seconds", 120, e -> e == null || (int) e >= 0);
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
