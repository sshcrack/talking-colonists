package me.sshcrack.mc_talking.config;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.controller.ControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.DoubleField;
import dev.isxander.yacl3.config.v2.api.autogen.DoubleSlider;
import dev.isxander.yacl3.config.v2.api.autogen.EnumCycler;
import dev.isxander.yacl3.config.v2.api.autogen.IntField;
import dev.isxander.yacl3.config.v2.api.autogen.ListGroup;
import dev.isxander.yacl3.config.v2.api.autogen.StringField;
import dev.isxander.yacl3.config.v2.api.autogen.TickBox;
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

    /** Movement speed multiplier when a citizen walks to the player on urgent contact. */
    public static final double CITIZEN_URGENT_WALK_SPEED = 1.2;

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
    @AutoGen(category = "general", group = "interaction")
    @TickBox
    @SerialEntry(comment = "Whether the citizens should respond if the player is in a group or not.")
    public boolean respondInGroups = false;

    @AutoGen(category = "general", group = "interaction")
    @TickBox
    @SerialEntry(comment = "If true, text messages from mumbling and citizen-to-citizen conversations will also be sent to nearby players in chat.")
    public boolean sendMumblingAndConversationsToChat = false;

    // Colony Statistics Mentions
    @AutoGen(category = "citizens")
    @TickBox
    @SerialEntry(comment = "If true, citizens will occasionally mention colony milestones (buildings built, mobs killed, etc.) in their idle mumbles and conversations.")
    public boolean enableColonyStatsMentions = true;

    @AutoGen(category = "general", group = "interaction")
    @TickBox
    @SerialEntry(comment = "If true, citizens continue wandering normally while in a player conversation / conversation with another citizen etc. "
            + "If false (default), they stay in place for the duration of the conversation.")
    public boolean continueWorkDuringConversation = false;

    // Citizen - Citizen Interaction (Conversations between them)
    @AutoGen(category = "citizens", group = "citizen_to_citizen")
    @TickBox
    @SerialEntry(comment = "If true, an AI will look at the conversation and take not of the most notable events that happend in that conversation. If not the real time AI is only able to update their memory mid conversation (less accurate)")
    public boolean enableConversationSummaryAndMemorize = false;

    @AutoGen(category = "citizens", group = "citizen_to_citizen")
    @TickBox
    @SerialEntry(comment = "If true, citizens will be able to start conversations with each other without player involvement.")
    public boolean enableCitizenToCitizenConversation = true;

    @AutoGen(category = "citizens", group = "citizen_to_citizen")
    @EnumCycler
    @SerialEntry(comment = "How citizen-to-citizen conversations are generated.\nLIVE_WEBSOCKETS: Two Gemini Live sessions feed audio to each other in real time - no Flash or TTS call needed.\nFLASH_TTS: Flash generates a script, then Gemini TTS renders multi-speaker audio. Higher quality but limited to ~10/day.\nAUTO (default): Tries Flash+TTS first; automatically falls back to Live WebSockets if the pipeline fails (e.g. quota exhausted).")
    public ConversationMode conversationMode = ConversationMode.AUTO;

    // Random citizen-to-citizen conversations
    @AutoGen(category = "citizens", group = "random_conversations")
    @TickBox
    @SerialEntry(comment = "If true, citizens will randomly start conversations with each other based on the chance below. Requires enableCitizenToCitizenConversation to be true.")
    public boolean enableRandomConversations = true;

    @AutoGen(category = "citizens", group = "random_conversations")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Chance (0.0-1.0) that a pair of nearby citizens start a random conversation per check interval")
    public double randomConversationChance = 0.05;

    @AutoGen(category = "citizens", group = "random_conversations")
    @IntField(min = 1, max = 10000)
    @SerialEntry(comment = "How often (in server ticks) to check for random citizen conversations. 20 ticks = 1 second")
    public int randomConversationCheckIntervalTicks = 400;

    // Pregeneration
    @AutoGen(category = "citizens", group = "pregeneration")
    @TickBox
    @SerialEntry(comment = "If true, the mod will pregenerate audio for citizen greetings and threats in the background to reduce audio latency.")
    public boolean enablePregeneration = true;

    @AutoGen(category = "citizens", group = "pregeneration")
    @DoubleField(min = 1.0, max = 20.0)
    @SerialEntry(comment = "Distance in blocks within which passing citizens will trigger their pregenerated greeting.")
    public double pregeneratedGreetingDistance = 6.0;

    @AutoGen(category = "citizens", group = "pregeneration")
    @IntField(min = 0, max = 60000)
    @SerialEntry(comment = "Cooldown in milliseconds between threat pregeneration plays for a citizen. Set to 0 to disable.")
    public int threatPlayCooldownMs = 15000;

    @AutoGen(category = "citizens", group = "pregeneration")
    @IntField(min = 0, max = 100)
    @SerialEntry(comment = "Maximum number of pregenerated greetings to store per citizen. Oldest greetings are discarded when limit is reached.")
    public int maxPregeneratedGreetingsPerCitizen = 5;

    @AutoGen(category = "citizens", group = "pregeneration")
    @IntField(min = 1, max = 10)
    @SerialEntry(comment = "Maximum number of pregenerated citizen-to-citizen greetings that may play in a single tick interval (default: 1). Increase only if you have a very large colony and want more ambient chatter.")
    public int maxGreetingsPerTickInterval = 1;

    // Resource Management
    @AutoGen(category = "general", group = "resource_management")
    @IntField(min = 1, max = 100)
    @SerialEntry(comment = "Maximum number of AI agents that can be activated at once (for free tier Flash2.0 this is limited to 3, for Flash2.5 to 1)")
    public int maxConcurrentAgents = 3;

    @AutoGen(category = "general", group = "interaction")
    @DoubleField(min = 1.0, max = 100.0)
    @SerialEntry(comment = "Maximum distance the player can be from a citizen before the conversation is ended")
    public double maxConversationDistance = 8.0;

    @AutoGen(category = "general", group = "interaction")
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
    @AutoGen(category = "citizens", group = "mumbling")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Chance (0.0-1.0) that a nearby citizen starts mumbling to themselves per check interval")
    public double mumblingChance = 0.05;

    @AutoGen(category = "citizens")
    @DoubleField(min = 1.0, max = 100.0)
    @SerialEntry(comment = "Distance in blocks within which a citizen can be triggered to mumble/start a conversation etc when a player is nearby")
    //TODO this is also used for greetings between citizens
    public double citizenInteractionRange = 10.0;

    @AutoGen(category = "citizens", group = "mumbling")
    @IntField(min = 1, max = 10000)
    @SerialEntry(comment = "How often (in server ticks) to check for citizens to trigger mumbling near players. 20 ticks = 1 second")
    public int mumblingCheckIntervalTicks = 200;

    // Citizen-Initiated Contact
    @AutoGen(category = "citizens", group = "citizen_contact")
    @TickBox
    @SerialEntry(comment = "If true, citizens with urgent needs will proactively speak to nearby players.")
    public boolean enableCitizenInitiatedContact = true;

    @AutoGen(category = "citizens", group = "citizen_contact")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Base chance (0.0-1.0) per check interval that an urgent citizen speaks to a nearby player. Multiplied by an urgency weight derived from the citizen's state (unhappiness, injury, hunger, etc.).")
    public double citizenContactBaseChance = 0.5;

    @AutoGen(category = "citizens", group = "citizen_contact")
    @IntField(min = 1, max = 10000)
    @SerialEntry(comment = "How often (in server ticks) to check for citizens that should initiate contact. 20 ticks = 1 second")
    public int citizenContactCheckIntervalTicks = 80;

    @AutoGen(category = "citizens", group = "citizen_contact")
    @TickBox
    @SerialEntry(comment = "When enabled, urgent citizens walk to the player and follow them, searching over a larger radius. The conversation auto-starts when they get close.")
    public boolean enableUrgentContactWalkToPlayer = true;

    @AutoGen(category = "citizens", group = "citizen_contact")
    @DoubleField(min = 5.0, max = 100.0)
    @SerialEntry(comment = "Search radius for urgent contacts when walk-to-player is enabled.")
    public double urgentContactSearchRange = 30.0;

    @AutoGen(category = "citizens", group = "citizen_contact")
    @DoubleSlider(min = 0.0, max = 10.0, step = 0.1)
    @SerialEntry(comment = "Extra urgency weight applied when the citizen is stuck (blocked by missing tools/items). Makes them much more likely to call for help.")
    public double blockingTaskUrgencyMultiplier = 3.0;

    @AutoGen(category = "citizens", group = "citizen_contact")
    @IntField(min = 0, max = 10000)
    @SerialEntry(comment = "Minimum cooldown in seconds between urgent citizen contacts for the same player. Set to 0 to disable.")
    public int playerUrgentContactCooldownSeconds = 60;

    @AutoGen(category = "citizens", group = "citizen_contact")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.01)
    @SerialEntry(comment = "Base weight for casual greetings (0.0-1.0). Even content citizens get this small chance to wave/say hello per check interval. Multiplied by citizenContactBaseChance.")
    public double citizenCasualGreetingWeight = 0.1;

    @AutoGen(category = "citizens", group = "pregeneration")
    @TickBox
    @SerialEntry(comment = "If true, the mod will pregenerate player-specific greetings for frequent citizen-player pairs in the background.")
    public boolean enablePlayerGreetingPregen = true;

    @AutoGen(category = "citizens", group = "pregeneration")
    @DoubleField(min = 1.0, max = 20.0)
    @SerialEntry(comment = "Distance in blocks within which a citizen will trigger their pregenerated player greeting.")
    public double playerGreetingDistance = 8.0;

    @AutoGen(category = "citizens", group = "pregeneration")
    @IntField(min = 0, max = 600)
    @SerialEntry(comment = "Cooldown in seconds between pregenerated player greetings for the same citizen-player pair.")
    public int playerGreetingCooldownSeconds = 60;

    @AutoGen(category = "citizens", group = "voice_chat")
    @TickBox
    @SerialEntry(comment = "If true, citizens will whisper when talking")
    public boolean citizenVoiceWhisper = true;

    @AutoGen(category = "citizens", group = "voice_chat")
    @IntField(min = 0)
    @SerialEntry(comment = "Max voice distance of the citizen. Use 0 to use default distance")
    public int citizenVoiceDistance = 0;

    // Per-citizen automatic-session cooldown
    @AutoGen(category = "citizens")
    @IntField(min = 0, max = 10000)
    @SerialEntry(comment = "Minimum number of seconds that must pass after a citizen's automatic session (mumble or citizen-to-citizen) ends before they can be selected for another one. Set to 0 to disable.")
    public int citizenCooldownSeconds = 120;

    // Post-Raid Trauma
    @AutoGen(category = "citizens", group = "raid_trauma")
    @IntField(min = 0, max = 7200)
    @SerialEntry(comment = "How long (in seconds) citizens express post-raid trauma in their prompts after a raid ends. Set to 0 to disable.")
    public int raidTraumaDurationSeconds = 1200;

    // Colony Events Window
    @AutoGen(category = "citizens", group = "colony_events")
    @IntField(min = 0, max = 7200)
    @SerialEntry(comment = "How long (in seconds) colony lifecycle events (births, deaths, job changes, building changes) appear in citizen prompts. Set to 0 to disable.")
    public int colonyEventWindowSeconds = 1200;

    // Rumor Mill
    @AutoGen(category = "citizens", group = "rumor_mill")
    @TickBox
    @SerialEntry(comment = "If true, citizens will share memories with each other as rumors, creating a living information ecosystem.")
    public boolean enableRumorMill = true;

    @AutoGen(category = "citizens", group = "rumor_mill")
    @IntField(min = 1, max = 72000)
    @SerialEntry(comment = "How often (in server ticks) to check for rumor propagation between nearby citizens. 20 ticks = 1 second.")
    public int rumorMillCheckIntervalTicks = 600;

    @AutoGen(category = "citizens", group = "rumor_mill")
    @DoubleField(min = 1.0, max = 50.0)
    @SerialEntry(comment = "Maximum distance in blocks for rumor propagation between citizens.")
    public double rumorMillRange = 8.0;

    @AutoGen(category = "citizens", group = "rumor_mill")
    @DoubleSlider(min = 0.0, max = 1.0, step = 0.05)
    @SerialEntry(comment = "Chance (0.0-1.0) that rumors are shared between a pair of nearby citizens per check.")
    public double rumorMillChancePerPair = 0.4;

    @AutoGen(category = "citizens", group = "rumor_mill")
    @IntField(min = 1, max = 100)
    @SerialEntry(comment = "Maximum number of rumor propagations per tick to bound server cost.")
    public int rumorMillMaxPropagationsPerTick = 3;

    // Broadcast System
    @AutoGen(category = "citizens", group = "broadcast")
    @TickBox
    @SerialEntry(comment = "If true, players can ask citizens to broadcast messages across the colony, which propagate through citizen-to-citizen interactions.")
    public boolean enableBroadcastPropagation = true;

    @AutoGen(category = "citizens", group = "broadcast")
    @IntField(min = 1, max = 72000)
    @SerialEntry(comment = "How often (in server ticks) to check for broadcast propagation between citizens. 20 ticks = 1 second.")
    public int broadcastPropagationIntervalTicks = 300;

    @AutoGen(category = "citizens", group = "broadcast")
    @IntField(min = 1, max = 100)
    @SerialEntry(comment = "Maximum number of broadcast propagations per tick.")
    public int broadcastMaxPropagationsPerTick = 5;

    @AutoGen(category = "citizens", group = "broadcast")
    @IntField(min = 0, max = 20)
    @SerialEntry(comment = "How many of the most recent broadcasts to include in a citizen's prompt.")
    public int maxBroadcastsInPrompt = 3;

    @AutoGen(category = "citizens", group = "broadcast")
    @IntField(min = 1, max = 100)
    @SerialEntry(comment = "Maximum broadcasts stored per citizen. Oldest discarded when limit reached.")
    public int maxBroadcastsStored = 20;

    @SerialEntry(comment = "Internal: config schema version for one-time migrations.")
    public int configVersion = 2;

    // Personality Archetypes
    @AutoGen(category = "citizens", group = "personality")
    @TickBox
    @SerialEntry(comment = "If true, each citizen is randomly assigned a personality archetype that influences their speech style and tone.")
    public boolean enablePersonalityArchetypes = true;

    @AutoGen(category = "citizens")
    @ListGroup(valueFactory = ToolListFactory.class, controllerFactory = ToolListFactory.class)
    @SerialEntry(comment = "Custom personality archetype strings added to the random pool citizens can be assigned. Each entry is a freeform instruction injected into the citizen's system prompt. Example: 'Always speak in rhyming couplets.'")
    public List<String> customPersonalityArchetypes = new ArrayList<>();

    // Colony Diplomacy
    @AutoGen(category = "citizens", group = "colony_diplomacy")
    @TickBox
    @SerialEntry(comment = "If true, citizens will reference neighboring colonies and their diplomatic standing (allies, enemies, etc.) in conversations.")
    public boolean enableColonyDiplomacy = true;

    // Memory Compaction
    @AutoGen(category = "citizens", group = "memory")
    @EnumCycler
    @SerialEntry(comment = "How memory compaction is performed. LIVE uses a text-only WebSocket session. FLASH uses the Gemini Flash API.")
    public MemoryMode memoryMode = MemoryMode.LIVE;

    @AutoGen(category = "citizens", group = "memory")
    @TickBox
    @SerialEntry(comment = "If true, citizen memories will be periodically compacted and summarized to prevent unbounded growth.")
    public boolean enableMemoryCompaction = true;

    @AutoGen(category = "citizens", group = "memory")
    @IntField(min = 20, max = 72000)
    @SerialEntry(comment = "How often (in server ticks) to check for memory compaction candidates. 20 ticks = 1 second.")
    public int memoryCompactionIntervalTicks = 100;

    @AutoGen(category = "citizens", group = "memory")
    @IntField(min = 1, max = 500)
    @SerialEntry(comment = "Maximum number of individual events/facts before compaction is triggered for a citizen.")
    public int memoryCompactionThreshold = 15;

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



    public static boolean hasGeminiApiKey() {
        String key = INSTANCE.instance().geminiApiKey;
        return key != null
                && !key.trim().isEmpty();
    }

    public static void loadConfig() {
        Path oldConfig = YACLPlatform.getConfigDir().resolve("mc_talking-common.toml");
        Path newConfig = YACLPlatform.getConfigDir().resolve("yacl-mc_talking.json5");

        boolean shouldMigrate = Files.exists(oldConfig) && !Files.exists(newConfig);
        INSTANCE.load();

        if (shouldMigrate) {
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
                            } catch (Exception e) {
                                McTalking.LOGGER.warn("Unknown AI model in old config: {}", val, e);
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
                            } catch (Exception e) {
                                McTalking.LOGGER.warn("Unknown modality in old config: {}", val, e);
                            }
                            break;
                        case "send_errors_to_players":
                            INSTANCE.instance().sendErrorsToPlayers = Boolean.parseBoolean(val);
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

        // One-time migration: LIVE_WEBSOCKETS → AUTO for existing JSON5 configs
        if (INSTANCE.instance().configVersion < 1) {
            if (INSTANCE.instance().conversationMode == ConversationMode.LIVE_WEBSOCKETS) {
                INSTANCE.instance().conversationMode = ConversationMode.AUTO;
                McTalking.LOGGER.info("[Config] Migrated conversationMode from LIVE_WEBSOCKETS to AUTO");
            }
            INSTANCE.instance().configVersion = 1;
            INSTANCE.save();
        }

        // One-time migration: old default citizenContactBaseChance (0.02) → new default (0.5)
        if (INSTANCE.instance().configVersion < 2) {
            if (INSTANCE.instance().citizenContactBaseChance <= 0.02) {
                INSTANCE.instance().citizenContactBaseChance = 0.5;
                McTalking.LOGGER.info("[Config] Migrated citizenContactBaseChance from 0.02 to 0.5");
            }
            INSTANCE.instance().configVersion = 2;
            INSTANCE.save();
        }

        // One-time migration: old default citizenContactCheckIntervalTicks (400) → new default (80)
        if (INSTANCE.instance().configVersion < 3) {
            if (INSTANCE.instance().citizenContactCheckIntervalTicks == 400) {
                INSTANCE.instance().citizenContactCheckIntervalTicks = 80;
                McTalking.LOGGER.info("[Config] Migrated citizenContactCheckIntervalTicks from 400 to 80");
            }
            INSTANCE.instance().configVersion = 3;
            INSTANCE.save();
        }
    }
}
