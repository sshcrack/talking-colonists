package me.sshcrack.mc_talking.config;

import com.ibm.icu.impl.breakiter.LSTMBreakEngine;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.controller.ControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.*;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import dev.isxander.yacl3.platform.YACLPlatform;
import me.sshcrack.mc_talking.McTalking;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for the McTalking mod.
 * Handles loading and managing configuration options.
 */
public class McTalkingConfig {
    public static final String FLASH_MODEL = "gemini-flash-lite-latest";
    public static final String TTS_MODEL = "gemini-3.1-flash-tts-preview";

    public static final ConfigClassHandler<McTalkingConfig> INSTANCE = ConfigClassHandler.createBuilder(McTalkingConfig.class)
            .id(YACLPlatform.rl("mc_talking", "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(YACLPlatform.getConfigDir().resolve("yacl-mc_talking.json5"))
                    .setJson5(true)
                    .build())
            .build();

    // API Configuration
    @AutoGen(category = "api")
    @StringField
    @SerialEntry(comment = "This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey")
    public String geminiApiKey = "";

    @AutoGen(category = "api")
    @EnumCycler
    @SerialEntry(comment = "What kind of AI model to use. Right now, this is the only one Google offers")
    public AvailableAI currentAiModel = AvailableAI.Flash3;

    // Language Configuration
    @AutoGen(category = "general")
    @StringField
    @SerialEntry(comment = "The language the AI should use to speak")
    public String language = "en-US";

    // Interaction Configuration
    @AutoGen(category = "general")
    @TickBox
    @SerialEntry(comment = "Whether the citizens should respond if the player is in a group or not.")
    public boolean respondInGroups = false;

    // Citizen - Citizen Interaction (Conversations between them)
    @AutoGen(category = "citizens")
    @TickBox
    @SerialEntry(comment = "If true, citizens will remember previous conversations that happened between them. Not recommended for free tier")
    public boolean enableCitizenMemory = false;

    @AutoGen(category = "citizens")
    @TickBox
    @SerialEntry(comment = "If true, citizens will be able to start conversations with each other without player involvement.")
    public boolean enableCitizenToCitizenConversation = true;

    @AutoGen(category = "citizens")
    @EnumCycler
    @SerialEntry(comment = "How citizen-to-citizen conversations are generated.\nLIVE_WEBSOCKETS (default/free): Two Gemini Live sessions feed audio to each other in real time - no Flash or TTS call needed.\nFLASH_TTS (higher quality): Flash generates a script, then Gemini TTS renders multi-speaker audio. This sounds more natural and has higher quality, but is limited to only 10 per DAY, so only use this if in paid tier")
    public ConversationMode conversationMode = ConversationMode.LIVE_WEBSOCKETS;

    // Random citizen-to-citizen conversations
    @AutoGen(category = "citizens")
    @TickBox
    @SerialEntry(comment = "If true, citizens will randomly start conversations with each other based on the chance below. Requires enableCitizenToCitizenConversation to be true.")
    public boolean enableRandomConversations = true;

    @AutoGen(category = "citizens")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Chance (0.0-1.0) that a pair of nearby citizens start a random conversation per check interval")
    public double randomConversationChance = 0.05;

    @AutoGen(category = "citizens")
    @IntField(min = 1, max = 10000)
    @SerialEntry(comment = "How often (in server ticks) to check for random citizen conversations. 20 ticks = 1 second")
    public int randomConversationCheckIntervalTicks = 400;

    // Resource Management
    @AutoGen(category = "general")
    @IntField(min = 1, max = 100)
    @SerialEntry(comment = "Maximum number of AI agents that can be activated at once (for free tier Flash2.0 this is limited to 3, for Flash2.5 to 1)")
    public int maxConcurrentAgents = 3;

    @AutoGen(category = "general")
    @DoubleField(min = 1.0, max = 100.0)
    @SerialEntry(comment = "Maximum distance the player can be from a citizen before the conversation is ended")
    public double maxConversationDistance = 8.0;

    @AutoGen(category = "general")
    @EnumCycler
    @SerialEntry(comment = "In which format the AI should respond. This can be text, audio or both.")
    public ModalityModes modality = ModalityModes.AUDIO;

    @AutoGen(category = "general")
    @ListGroup(valueFactory = ToolListFactory.class, controllerFactory = ToolListFactory.class)
    @SerialEntry(comment = "List of disabled tools for the AI. These tools can't be used by the AI to perform actions. Use the /list_tools command to see a list of the available tools.")
    public List<String> disabledTools = new ArrayList<>();

    @AutoGen(category = "general")
    @TickBox
    @SerialEntry(comment = "If true, errors will be sent to players that have OP permissions. If false, errors will only be logged to the console.")
    public boolean sendErrorsToPlayers = true;

    // Proximity Mumbling
    @AutoGen(category = "citizens")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Chance (0.0-1.0) that a nearby citizen starts mumbling to themselves per check interval")
    public double mumblingChance = 0.05;

    @AutoGen(category = "citizens")
    @DoubleField(min = 1.0, max = 100.0)
    @SerialEntry(comment = "Distance in blocks within which a citizen can be triggered to mumble when a player is nearby")
    public double mumblingDetectionRange = 10.0;

    @AutoGen(category = "citizens")
    @IntField(min = 1, max = 10000)
    @SerialEntry(comment = "How often (in server ticks) to check for citizens to trigger mumbling near players. 20 ticks = 1 second")
    public int mumblingCheckIntervalTicks = 200;

    // Citizen-Initiated Contact
    @AutoGen(category = "citizens")
    @TickBox
    @SerialEntry(comment = "If true, citizens with urgent needs will proactively speak to nearby players.")
    public boolean enableCitizenInitiatedContact = true;

    @AutoGen(category = "citizens")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Base chance (0.0-1.0) per check interval that an urgent citizen speaks to a nearby player. Multiplied by an urgency weight derived from the citizen's state (unhappiness, injury, hunger, etc.).")
    public double citizenContactBaseChance = 0.02;

    @AutoGen(category = "citizens")
    @IntField(min = 1, max = 10000)
    @SerialEntry(comment = "How often (in server ticks) to check for citizens that should initiate contact. 20 ticks = 1 second")
    public int citizenContactCheckIntervalTicks = 400;

    // Per-citizen automatic-session cooldown
    @AutoGen(category = "citizens")
    @IntField(min = 0, max = 10000)
    @SerialEntry(comment = "Minimum number of seconds that must pass after a citizen's automatic session (mumble or citizen-to-citizen) ends before they can be selected for another one. Set to 0 to disable.")
    public int citizenCooldownSeconds = 120;

    public static class ToolListFactory implements ListGroup.ValueFactory<String>, ListGroup.ControllerFactory<String> {
        @Override
        public String provideNewValue() {
            return "";
        }

        @Override
        public ControllerBuilder<String> createController(ListGroup annotation, ConfigField<List<String>> field, dev.isxander.yacl3.config.v2.api.autogen.OptionAccess storage, Option<String> option) {
            return StringControllerBuilder.create(option);
        }
    }

    public static void loadConfig() {
        Path oldConfig = YACLPlatform.getConfigDir().resolve("mc_talking-common.toml");
        Path newConfig = YACLPlatform.getConfigDir().resolve("yacl-mc_talking.json5");

        INSTANCE.load();

        if (Files.exists(oldConfig) && !Files.exists(newConfig)) {
            McTalking.LOGGER.info("Migrating old TOML config to YACL JSON5 config...");
            try {
                List<String> lines = Files.readAllLines(oldConfig);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] parts = line.split("=", 2);
                    if (parts.length != 2) continue;
                    String key = parts[0].trim();
                    String val = parts[1].trim();

                    if (val.startsWith("\"") && val.endsWith("\"")) {
                        val = val.substring(1, val.length() - 1);
                    }

                    switch (key) {
                        case "gemini_key":
                            INSTANCE.instance().geminiApiKey = val;
                            break;
                        case "ai_model":
                            try {
                                INSTANCE.instance().currentAiModel = AvailableAI.valueOf(val);
                            } catch (Exception ignored) {
                            }
                            break;
                        case "language":
                            INSTANCE.instance().language = val;
                            break;
                        case "respond_in_group":
                            INSTANCE.instance().respondInGroups = java.lang.Boolean.parseBoolean(val);
                            break;
                        case "max_conversation_distance":
                            INSTANCE.instance().maxConversationDistance = Double.parseDouble(val);
                            break;
                        case "ai_modality":
                            try {
                                INSTANCE.instance().modality = ModalityModes.valueOf(val);
                            } catch (Exception ignored) {
                            }
                            break;
                        case "send_errors_to_players":
                            INSTANCE.instance().sendErrorsToPlayers = java.lang.Boolean.parseBoolean(val);
                            break;
                        case "enable_citizen_memory":
                            INSTANCE.instance().enableCitizenMemory = java.lang.Boolean.parseBoolean(val);
                            break;
                        case "enable_citizen_to_citizen_conversation":
                            INSTANCE.instance().enableCitizenToCitizenConversation = java.lang.Boolean.parseBoolean(val);
                            break;
                        case "disabled_tools":
                            if (!val.equals("[]")) {
                                String toolsStr = val.replaceAll("[\\[\\]\"]", "");
                                String[] tools = toolsStr.split(",");
                                List<String> disabledTools = new ArrayList<>();
                                for (String t : tools) {
                                    if (!t.trim().isEmpty()) {
                                        disabledTools.add(t.trim());
                                    }
                                }
                                INSTANCE.instance().disabledTools = disabledTools;
                            }
                            break;
                        case "conversation_mode":
                            try {
                                INSTANCE.instance().conversationMode = ConversationMode.valueOf(val);
                            } catch (Exception ignored) {
                            }
                            break;
                        case "enable_random_conversations":
                            INSTANCE.instance().enableRandomConversations = java.lang.Boolean.parseBoolean(val);
                            break;
                        case "random_conversation_chance":
                            INSTANCE.instance().randomConversationChance = Double.parseDouble(val);
                            break;
                        case "random_conversation_check_interval_ticks":
                            INSTANCE.instance().randomConversationCheckIntervalTicks = Integer.parseInt(val);
                            break;
                        case "max_concurrent_agents":
                            INSTANCE.instance().maxConcurrentAgents = Integer.parseInt(val);
                            break;
                        case "mumbling_chance":
                            INSTANCE.instance().mumblingChance = Double.parseDouble(val);
                            break;
                        case "mumbling_detection_range":
                            INSTANCE.instance().mumblingDetectionRange = Double.parseDouble(val);
                            break;
                        case "mumbling_check_interval_ticks":
                            INSTANCE.instance().mumblingCheckIntervalTicks = Integer.parseInt(val);
                            break;
                        case "enable_citizen_initiated_contact":
                            INSTANCE.instance().enableCitizenInitiatedContact = java.lang.Boolean.parseBoolean(val);
                            break;
                        case "citizen_contact_base_chance":
                            INSTANCE.instance().citizenContactBaseChance = Double.parseDouble(val);
                            break;
                        case "citizen_contact_check_interval_ticks":
                            INSTANCE.instance().citizenContactCheckIntervalTicks = Integer.parseInt(val);
                            break;
                        case "citizen_cooldown_seconds":
                            INSTANCE.instance().citizenCooldownSeconds = Integer.parseInt(val);
                            break;
                        default:
                            McTalking.LOGGER.info("Unknown config key in old TOML config: {}", key);
                    }
                }

                INSTANCE.save();
                Files.deleteIfExists(oldConfig);
                McTalking.LOGGER.info("Successfully migrated old TOML config.");
            } catch (Exception e) {
                McTalking.LOGGER.error("Failed to migrate old TOML config", e);
            }
        }
    }
}
