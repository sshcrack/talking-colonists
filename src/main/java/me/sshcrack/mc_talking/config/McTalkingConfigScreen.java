package me.sshcrack.mc_talking.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionFlag;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.DropdownStringControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumDropdownControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import me.sshcrack.mc_talking.api.provider.AiRegistry;
import me.sshcrack.mc_talking.api.provider.ConfigField;
import me.sshcrack.mc_talking.api.provider.PresetDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class McTalkingConfigScreen {
    private McTalkingConfigScreen() {
        /* This utility class should not be instantiated */
    }


    private static final Gson GSON = new Gson();

    private static class PresetDropdownValues {
        final List<String> displayNames;
        final java.util.Map<String, String> displayToFullId;
        final java.util.Map<String, String> fullIdToDisplay;
        final String defaultValue;

        PresetDropdownValues(Map<String, String> displayToFullId, String defaultValue) {
            this.displayToFullId = displayToFullId;
            this.fullIdToDisplay = new java.util.LinkedHashMap<>();
            for (var e : displayToFullId.entrySet()) fullIdToDisplay.put(e.getValue(), e.getKey());
            this.displayNames = List.copyOf(displayToFullId.keySet());
            this.defaultValue = defaultValue;
        }

        String toDisplay(String fullId) {
            return fullIdToDisplay.getOrDefault(fullId, fullId);
        }

        String toFullId(String display) {
            String id = displayToFullId.get(display);
            return id != null ? id : display;
        }
    }

    private static PresetDropdownValues buildFilteredPresetDropdownValues(java.util.function.Predicate<PresetDefinition> filter) {
        var presets = AiRegistry.getPresets();
        var map = new java.util.LinkedHashMap<String, String>();
        for (var entry : presets.entrySet()) {
            if (!filter.test(entry.getValue())) continue;
            var display = Component.translatable(entry.getValue().displayName()).getString();
            map.put(display, entry.getKey());
        }
        if (map.isEmpty()) {
            map.put("Gemini Live", "gemini_live_lib.live_live");
            map.put("Gemini Flash + TTS", "gemini_live_lib.flash_tts");
            map.put("Gemini Flash", "gemini_live_lib.flash");
            map.put("Gemini Live (Text)", "gemini_live_lib.live");
        }
        return new PresetDropdownValues(map, map.values().iterator().next());
    }

    private static PresetDropdownValues livePresetVals;
    private static PresetDropdownValues pregenPresetVals;
    private static PresetDropdownValues bgPresetVals;

    private static void ensurePresetValues() {
        if (livePresetVals != null) return;
        livePresetVals = buildFilteredPresetDropdownValues(PresetDefinition::supportsLive);
        pregenPresetVals = buildFilteredPresetDropdownValues(PresetDefinition::supportsPregenerated);
        bgPresetVals = buildFilteredPresetDropdownValues(PresetDefinition::supportsBackground);
    }

    public static Screen createScreen(Screen parent) {
        var config = McTalkingConfig.INSTANCE.instance();

        var builder = YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Talking Colonists Config"))
            .save(McTalkingConfig.INSTANCE::save);

        // --- AI Providers category ---
        builder.category(buildAiProvidersCategory(config));

        // --- General category ---
        builder.category(buildGeneralCategory(config));

        // --- Citizens category ---
        builder.category(buildCitizensCategory(config));

        return builder.build().generateScreen(parent);
    }

    private static ConfigCategory buildAiProvidersCategory(McTalkingConfig config) {
        var cat = ConfigCategory.createBuilder()
            .name(Component.literal("AI Providers"))
            .tooltip(Component.literal("Select AI providers and presets for different conversation types"));

        // --- Preset selection group ---
        var presetGroup = OptionGroup.createBuilder()
            .name(Component.literal("Presets"))
            .description(OptionDescription.of(Component.literal("Choose which AI preset to use for each conversation category. Presets are registered by installed provider mods.")));

        ensurePresetValues();

        // Live preset
        presetGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Live Conversation Preset"))
            .description(OptionDescription.of(Component.literal("Preset for live player-citizen conversations")))
            .binding(livePresetVals.defaultValue,
                () -> livePresetVals.toDisplay(config.livePreset),
                v -> config.livePreset = livePresetVals.toFullId(v))
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(livePresetVals.displayNames)
                .allowAnyValue(false)
                .allowEmptyValue(false))
            .build());

        // Live fallback
        presetGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Live Fallback Preset"))
            .description(OptionDescription.of(Component.literal("Fallback if the primary live preset is unavailable")))
            .binding("",
                () -> livePresetVals.toDisplay(config.liveFallback),
                v -> config.liveFallback = livePresetVals.toFullId(v))
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(livePresetVals.displayNames)
                .allowAnyValue(false)
                .allowEmptyValue(true))
            .build());

        // Pregenerated preset
        presetGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Pregenerated Conversation Preset"))
            .description(OptionDescription.of(Component.literal("Preset for pregenerated conversations (greetings, citizen-to-citizen)")))
            .binding(pregenPresetVals.defaultValue,
                () -> pregenPresetVals.toDisplay(config.pregeneratedPreset),
                v -> config.pregeneratedPreset = pregenPresetVals.toFullId(v))
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(pregenPresetVals.displayNames)
                .allowAnyValue(false)
                .allowEmptyValue(false))
            .build());

        // Pregenerated fallback
        presetGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Pregenerated Fallback Preset"))
            .description(OptionDescription.of(Component.literal("Fallback if the primary pregenerated preset is unavailable")))
            .binding("",
                () -> pregenPresetVals.toDisplay(config.pregeneratedFallback),
                v -> config.pregeneratedFallback = pregenPresetVals.toFullId(v))
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(pregenPresetVals.displayNames)
                .allowAnyValue(false)
                .allowEmptyValue(true))
            .build());

        // Background preset
        presetGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Background Task Preset"))
            .description(OptionDescription.of(Component.literal("Preset for background tasks (memory compaction, pregeneration)")))
            .binding(bgPresetVals.defaultValue,
                () -> bgPresetVals.toDisplay(config.backgroundPreset),
                v -> config.backgroundPreset = bgPresetVals.toFullId(v))
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(bgPresetVals.displayNames)
                .allowAnyValue(false)
                .allowEmptyValue(false))
            .build());

        // Background fallback
        presetGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Background Fallback Preset"))
            .description(OptionDescription.of(Component.literal("Fallback if the primary background preset is unavailable")))
            .binding("",
                () -> bgPresetVals.toDisplay(config.backgroundFallback),
                v -> config.backgroundFallback = bgPresetVals.toFullId(v))
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(bgPresetVals.displayNames)
                .allowAnyValue(false)
                .allowEmptyValue(true))
            .build());

        cat.group(presetGroup.build());

        // --- Provider configuration group ---
        var providerConfigGroup = OptionGroup.createBuilder()
            .name(Component.literal("Provider Configuration"))
            .description(OptionDescription.of(Component.literal("Configure individual AI providers that are installed")));

        // Parse current providerConfig JSON
        JsonObject providerConfigJson;
        try {
            providerConfigJson = GSON.fromJson(config.providerConfig, JsonObject.class);
        } catch (Exception e) {
            providerConfigJson = new JsonObject();
        }
        if (providerConfigJson == null) providerConfigJson = new JsonObject();

        // Get registered config fields from provider mods
        var providerConfigFields = getProviderConfigFieldsSafe();

        for (var entry : providerConfigFields.entrySet()) {
            String modId = entry.getKey();
            List<ConfigField> fields = entry.getValue();

            for (ConfigField field : fields) {
                String translationKey = "mc_talking.config.provider." + modId + "." + field.key();
                String translationName = translationKey;
                String comment;
                try {
                    comment = net.minecraft.client.resources.language.I18n.get(translationKey + ".desc");
                    if (comment.equals(translationKey + ".desc")) comment = "";
                } catch (Exception e) {
                    comment = "";
                }

                // Read current value from JSON
                JsonObject modCfg = providerConfigJson.has(modId)
                    ? providerConfigJson.getAsJsonObject(modId) : new JsonObject();

                switch (field.type()) {
                    case STRING -> {
                        String defaultValue = field.defaultValue() instanceof String s ? s : "";
                        providerConfigGroup.option(Option.<String>createBuilder()
                            .name(Component.translatable(translationName))
                            .description(OptionDescription.of(Component.literal(comment)))
                            .binding(defaultValue,
                                () -> {
                                    var j = parseProviderConfig();
                                    return j.has(modId) && j.getAsJsonObject(modId).has(field.key())
                                        ? j.getAsJsonObject(modId).get(field.key()).getAsString() : defaultValue;
                                },
                                v -> setProviderConfigValue(modId, field.key(), v))
                            .controller(StringControllerBuilder::create)
                            .flag(OptionFlag.GAME_RESTART)
                            .build());
                    }
                    case INTEGER -> {
                        int defaultValue = field.defaultValue() instanceof Number n ? n.intValue() : 0;
                        int min = field.min();
                        int max = field.max() > 0 ? field.max() : Integer.MAX_VALUE;
                        providerConfigGroup.option(Option.<Integer>createBuilder()
                            .name(Component.translatable(translationName))
                            .description(OptionDescription.of(Component.literal(comment)))
                            .binding(defaultValue,
                                () -> {
                                    var j = parseProviderConfig();
                                    return j.has(modId) && j.getAsJsonObject(modId).has(field.key())
                                        ? j.getAsJsonObject(modId).get(field.key()).getAsInt() : defaultValue;
                                },
                                v -> setProviderConfigValue(modId, field.key(), v))
                            .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(min, max))
                            .flag(OptionFlag.GAME_RESTART)
                            .build());
                    }
                    case DOUBLE -> {
                        double defaultValue = field.defaultValue() instanceof Number n ? n.doubleValue() : 0.0;
                        providerConfigGroup.option(Option.<Double>createBuilder()
                            .name(Component.translatable(translationName))
                            .description(OptionDescription.of(Component.literal(comment)))
                            .binding(defaultValue,
                                () -> {
                                    var j = parseProviderConfig();
                                    return j.has(modId) && j.getAsJsonObject(modId).has(field.key())
                                        ? j.getAsJsonObject(modId).get(field.key()).getAsDouble() : defaultValue;
                                },
                                v -> setProviderConfigValue(modId, field.key(), v))
                            .controller(DoubleFieldControllerBuilder::create)
                            .flag(OptionFlag.GAME_RESTART)
                            .build());
                    }
                    case BOOLEAN -> {
                        boolean defaultValue = field.defaultValue() instanceof Boolean b && b;
                        providerConfigGroup.option(Option.<Boolean>createBuilder()
                            .name(Component.translatable(translationName))
                            .description(OptionDescription.of(Component.literal(comment)))
                            .binding(defaultValue,
                                () -> {
                                    var j = parseProviderConfig();
                                    return j.has(modId) && j.getAsJsonObject(modId).has(field.key())
                                        ? j.getAsJsonObject(modId).get(field.key()).getAsBoolean() : defaultValue;
                                },
                                v -> setProviderConfigValue(modId, field.key(), v))
                            .controller(TickBoxControllerBuilder::create)
                            .flag(OptionFlag.GAME_RESTART)
                            .build());
                    }
                }
            }
        }

        // If no provider config fields are registered, show a message
        if (providerConfigFields.isEmpty()) {
            providerConfigGroup.option(ButtonOption.createBuilder()
                .name(Component.literal("No provider mods installed"))
                .text(Component.literal("Install Gemini Live Library"))
                .action((screen, opt) -> {})
                .build());
        }

        cat.group(providerConfigGroup.build());

        // --- Create Preset button ---
        cat.option(ButtonOption.createBuilder()
            .name(Component.literal("Create Preset"))
            .text(Component.literal("Open Preset Editor"))
            .description(OptionDescription.of(Component.literal("Create a new AI preset with custom providers and configuration.")))
            .action((screen, opt) -> openPresetCreationScreen(screen))
            .build());

        return cat.build();
    }

    private static Map<String, List<ConfigField>> getProviderConfigFieldsSafe() {
        return AiRegistry.getProviderConfigFields();
    }

    private static JsonObject parseProviderConfig() {
        try {
            var j = GSON.fromJson(McTalkingConfig.INSTANCE.instance().providerConfig, JsonObject.class);
            return j != null ? j : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void setProviderConfigValue(String modId, String key, Object value) {
        var config = McTalkingConfig.INSTANCE.instance();
        JsonObject root;
        try {
            root = GSON.fromJson(config.providerConfig, JsonObject.class);
        } catch (Exception e) {
            root = new JsonObject();
        }
        if (root == null) root = new JsonObject();

        JsonObject modCfg = root.has(modId) ? root.getAsJsonObject(modId) : new JsonObject();
        if (value instanceof String s) modCfg.addProperty(key, s);
        else if (value instanceof Number n) modCfg.addProperty(key, n);
        else if (value instanceof Boolean b) modCfg.addProperty(key, b);
        root.add(modId, modCfg);

        config.providerConfig = GSON.toJson(root);
    }

    // -------------------------------------------------------------------------
    // General category
    // -------------------------------------------------------------------------

    private static ConfigCategory buildGeneralCategory(McTalkingConfig config) {
        var cat = ConfigCategory.createBuilder()
            .name(Component.literal("General"))
            .tooltip(Component.literal("General conversation settings"));

        // Language
        cat.option(Option.<String>createBuilder()
            .name(Component.literal("Language"))
            .description(OptionDescription.of(Component.literal("The language the AI should use to speak")))
            .binding("en-US", () -> config.language, v -> config.language = v)
            .controller(StringControllerBuilder::create)
            .build());

        // Modality
        cat.option(Option.<ModalityModes>createBuilder()
            .name(Component.literal("Response Mode"))
            .description(OptionDescription.of(Component.literal("In which format the AI should respond. Text, audio, or both.")))
            .binding(ModalityModes.AUDIO, () -> config.modality, v -> config.modality = v)
            .controller(opt -> EnumDropdownControllerBuilder.create(opt))
            .build());

        // Interaction group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Interaction"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Respond in Groups"))
                .description(OptionDescription.of(Component.literal("Whether citizens should respond if the player is in a group.")))
                .binding(false, () -> config.respondInGroups, v -> config.respondInGroups = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Send Chat Messages"))
                .description(OptionDescription.of(Component.literal("Send mumbling and citizen-to-citizen text to nearby players in chat.")))
                .binding(false, () -> config.sendMumblingAndConversationsToChat, v -> config.sendMumblingAndConversationsToChat = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Continue Working"))
                .description(OptionDescription.of(Component.literal("Citizens continue wandering normally while in a conversation.")))
                .binding(false, () -> config.continueWorkDuringConversation, v -> config.continueWorkDuringConversation = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Max Conversation Distance"))
                .description(OptionDescription.of(Component.literal("Maximum distance before a conversation is ended.")))
                .binding(8.0, () -> config.maxConversationDistance, v -> config.maxConversationDistance = v)
                .controller(opt -> DoubleFieldControllerBuilder.create(opt).range(1.0, 100.0))
                .build())
            .build());

        // Resource Management group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Resource Management"))
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Concurrent Agents"))
                .description(OptionDescription.of(Component.literal("Maximum number of AI agents that can be active at once.")))
                .binding(3, () -> config.maxConcurrentAgents, v -> config.maxConcurrentAgents = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 100))
                .flag(OptionFlag.GAME_RESTART)
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Background Connections"))
                .description(OptionDescription.of(Component.literal("Maximum concurrent background connections for memory compaction and pregeneration.")))
                .binding(3, () -> config.maxConcurrentBackground, v -> config.maxConcurrentBackground = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 10))
                .flag(OptionFlag.GAME_RESTART)
                .build())
            .build());

        // Errors
        cat.option(Option.<Boolean>createBuilder()
            .name(Component.literal("Send Errors to Players"))
            .description(OptionDescription.of(Component.literal("Send errors to OP players in chat.")))
            .binding(true, () -> config.sendErrorsToPlayers, v -> config.sendErrorsToPlayers = v)
            .controller(TickBoxControllerBuilder::create)
            .build());

        // Advanced button to open full auto-generated screen
        cat.option(ButtonOption.createBuilder()
            .name(Component.literal("Advanced Settings"))
            .text(Component.literal("Open Full Config"))
            .description(OptionDescription.of(Component.literal("Open the complete auto-generated config screen with all settings.")))
            .action((currentScreen, opt) -> {
                var fullScreen = McTalkingConfig.INSTANCE.generateGui().generateScreen(currentScreen);
                net.minecraft.client.Minecraft.getInstance().setScreen(fullScreen);
            })
            .build());

        return cat.build();
    }

    // -------------------------------------------------------------------------
    // Citizens category
    // -------------------------------------------------------------------------

    private static ConfigCategory buildCitizensCategory(McTalkingConfig config) {
        var cat = ConfigCategory.createBuilder()
            .name(Component.literal("Citizens"))
            .tooltip(Component.literal("Citizen behavior and conversation settings"));

        // Colony Stats
        cat.option(Option.<Boolean>createBuilder()
            .name(Component.literal("Colony Stats Mentions"))
            .description(OptionDescription.of(Component.literal("Citizens occasionally mention colony milestones in their conversations.")))
            .binding(true, () -> config.enableColonyStatsMentions, v -> config.enableColonyStatsMentions = v)
            .controller(TickBoxControllerBuilder::create)
            .build());

        // Citizen-to-Citizen group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Citizen-to-Citizen"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Conversation Summary"))
                .description(OptionDescription.of(Component.literal("AI summarizes notable events from conversations for memory.")))
                .binding(false, () -> config.enableConversationSummaryAndMemorize, v -> config.enableConversationSummaryAndMemorize = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable C2C Conversations"))
                .description(OptionDescription.of(Component.literal("Citizens can start conversations with each other.")))
                .binding(true, () -> config.enableCitizenToCitizenConversation, v -> config.enableCitizenToCitizenConversation = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .build());

        // Random Conversations group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Random Conversations"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Random C2C"))
                .description(OptionDescription.of(Component.literal("Citizens randomly start conversations based on chance below.")))
                .binding(true, () -> config.enableRandomConversations, v -> config.enableRandomConversations = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Random C2C Chance"))
                .description(OptionDescription.of(Component.literal("Chance (0.0-1.0) that nearby citizens start a random conversation per check.")))
                .binding(0.05, () -> config.randomConversationChance, v -> config.randomConversationChance = v)
                .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 1.0).step(0.01))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Random C2C Interval"))
                .description(OptionDescription.of(Component.literal("How often (ticks) to check for random conversations. 20 ticks = 1 second.")))
                .binding(400, () -> config.randomConversationCheckIntervalTicks, v -> config.randomConversationCheckIntervalTicks = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 10000))
                .build())
            .build());

        // Pregeneration group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Pregeneration"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Pregeneration"))
                .description(OptionDescription.of(Component.literal("Pregenerate greetings and threats to reduce latency.")))
                .binding(true, () -> config.enablePregeneration, v -> config.enablePregeneration = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Greeting Distance"))
                .description(OptionDescription.of(Component.literal("Distance within which passing citizens trigger pregenerated greetings.")))
                .binding(6.0, () -> config.pregeneratedGreetingDistance, v -> config.pregeneratedGreetingDistance = v)
                .controller(opt -> DoubleFieldControllerBuilder.create(opt).range(1.0, 20.0))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Threat Cooldown"))
                .description(OptionDescription.of(Component.literal("Cooldown (ms) between threat pregeneration plays.")))
                .binding(15000, () -> config.threatPlayCooldownMs, v -> config.threatPlayCooldownMs = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 60000))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Greetings per Citizen"))
                .description(OptionDescription.of(Component.literal("Maximum pregenerated greetings stored per citizen.")))
                .binding(5, () -> config.maxPregeneratedGreetingsPerCitizen, v -> config.maxPregeneratedGreetingsPerCitizen = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 100))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Greetings per Tick"))
                .description(OptionDescription.of(Component.literal("Maximum greetings that may play in a single tick interval.")))
                .binding(1, () -> config.maxGreetingsPerTickInterval, v -> config.maxGreetingsPerTickInterval = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 10))
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Player Greeting Pregen"))
                .description(OptionDescription.of(Component.literal("Pregenerate player-specific greetings for frequent pairs.")))
                .binding(true, () -> config.enablePlayerGreetingPregen, v -> config.enablePlayerGreetingPregen = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Player Greeting Distance"))
                .description(OptionDescription.of(Component.literal("Distance for triggering pregenerated player greetings.")))
                .binding(8.0, () -> config.playerGreetingDistance, v -> config.playerGreetingDistance = v)
                .controller(opt -> DoubleFieldControllerBuilder.create(opt).range(1.0, 20.0))
                .build())
            .build());

        // Mumbling group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Mumbling"))
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Mumbling Chance"))
                .description(OptionDescription.of(Component.literal("Chance (0.0-1.0) a nearby citizen starts mumbling per check.")))
                .binding(0.05, () -> config.mumblingChance, v -> config.mumblingChance = v)
                .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 1.0).step(0.01))
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Interaction Range"))
                .description(OptionDescription.of(Component.literal("Range (blocks) for citizen interaction triggers.")))
                .binding(10.0, () -> config.citizenInteractionRange, v -> config.citizenInteractionRange = v)
                .controller(opt -> DoubleFieldControllerBuilder.create(opt).range(1.0, 100.0))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Mumbling Check Interval"))
                .description(OptionDescription.of(Component.literal("How often (ticks) to check for mumbling citizens.")))
                .binding(200, () -> config.mumblingCheckIntervalTicks, v -> config.mumblingCheckIntervalTicks = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 10000))
                .build())
            .build());

        // Citizen Contact group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Citizen-Initiated Contact"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Contact"))
                .description(OptionDescription.of(Component.literal("Citizens with urgent needs speak to nearby players.")))
                .binding(true, () -> config.enableCitizenInitiatedContact, v -> config.enableCitizenInitiatedContact = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Contact Base Chance"))
                .description(OptionDescription.of(Component.literal("Base chance (0.0-1.0) per check for urgent citizen contact.")))
                .binding(0.5, () -> config.citizenContactBaseChance, v -> config.citizenContactBaseChance = v)
                .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 1.0).step(0.01))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Contact Check Interval"))
                .description(OptionDescription.of(Component.literal("How often (ticks) to check for citizens that need to contact players.")))
                .binding(80, () -> config.citizenContactCheckIntervalTicks, v -> config.citizenContactCheckIntervalTicks = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 10000))
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Walk to Player"))
                .description(OptionDescription.of(Component.literal("Urgent citizens walk to the player and follow them.")))
                .binding(true, () -> config.enableUrgentContactWalkToPlayer, v -> config.enableUrgentContactWalkToPlayer = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Urgent Search Range"))
                .description(OptionDescription.of(Component.literal("Search radius for urgent contacts with walk-to-player enabled.")))
                .binding(30.0, () -> config.urgentContactSearchRange, v -> config.urgentContactSearchRange = v)
                .controller(opt -> DoubleFieldControllerBuilder.create(opt).range(5.0, 100.0))
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Blocking Task Multiplier"))
                .description(OptionDescription.of(Component.literal("Extra urgency weight when a citizen is blocked by missing tools.")))
                .binding(3.0, () -> config.blockingTaskUrgencyMultiplier, v -> config.blockingTaskUrgencyMultiplier = v)
                .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 10.0).step(0.1))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Player Contact Cooldown"))
                .description(OptionDescription.of(Component.literal("Minimum cooldown (seconds) between urgent contacts per player.")))
                .binding(60, () -> config.playerUrgentContactCooldownSeconds, v -> config.playerUrgentContactCooldownSeconds = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 10000))
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Casual Greeting Weight"))
                .description(OptionDescription.of(Component.literal("Weight for casual greetings from content citizens.")))
                .binding(0.1, () -> config.citizenCasualGreetingWeight, v -> config.citizenCasualGreetingWeight = v)
                .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 1.0).step(0.01))
                .build())
            .build());

        // Voice Chat group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Voice Chat"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Whisper"))
                .description(OptionDescription.of(Component.literal("Citizens whisper when talking.")))
                .binding(true, () -> config.citizenVoiceWhisper, v -> config.citizenVoiceWhisper = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Voice Distance"))
                .description(OptionDescription.of(Component.literal("Max voice distance of citizens. 0 = default.")))
                .binding(0, () -> config.citizenVoiceDistance, v -> config.citizenVoiceDistance = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                .build())
            .build());

        // Citizen Cooldown
        cat.option(Option.<Integer>createBuilder()
            .name(Component.literal("Citizen Cooldown"))
            .description(OptionDescription.of(Component.literal("Minimum seconds between automatic sessions (mumble/C2C) for a citizen. 0 = no cooldown.")))
            .binding(120, () -> config.citizenCooldownSeconds, v -> config.citizenCooldownSeconds = v)
            .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 10000))
            .build());

        // Raid Trauma group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Raid Trauma"))
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Trauma Duration"))
                .description(OptionDescription.of(Component.literal("Seconds citizens express post-raid trauma. 0 = disable.")))
                .binding(1200, () -> config.raidTraumaDurationSeconds, v -> config.raidTraumaDurationSeconds = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 7200))
                .build())
            .build());

        // Colony Events
        cat.option(Option.<Integer>createBuilder()
            .name(Component.literal("Colony Event Window"))
            .description(OptionDescription.of(Component.literal("Seconds colony events appear in citizen prompts. 0 = disable.")))
            .binding(1200, () -> config.colonyEventWindowSeconds, v -> config.colonyEventWindowSeconds = v)
            .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 7200))
            .build());

        // Rumor Mill group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Rumor Mill"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Rumor Mill"))
                .description(OptionDescription.of(Component.literal("Citizens share memories as rumors.")))
                .binding(true, () -> config.enableRumorMill, v -> config.enableRumorMill = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Rumor Check Interval"))
                .description(OptionDescription.of(Component.literal("Ticks between rumor propagation checks.")))
                .binding(600, () -> config.rumorMillCheckIntervalTicks, v -> config.rumorMillCheckIntervalTicks = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 72000))
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Rumor Range"))
                .description(OptionDescription.of(Component.literal("Max distance for rumor propagation.")))
                .binding(12.0, () -> config.rumorMillRange, v -> config.rumorMillRange = v)
                .controller(opt -> DoubleFieldControllerBuilder.create(opt).range(1.0, 100.0))
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Rumor Chance per Pair"))
                .description(OptionDescription.of(Component.literal("Chance (0.0-1.0) rumors are shared per pair per check.")))
                .binding(0.4, () -> config.rumorMillChancePerPair, v -> config.rumorMillChancePerPair = v)
                .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 1.0).step(0.05))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Rumors per Tick"))
                .description(OptionDescription.of(Component.literal("Max rumor propagations per tick.")))
                .binding(3, () -> config.rumorMillMaxPropagationsPerTick, v -> config.rumorMillMaxPropagationsPerTick = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 100))
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Rumor Talking"))
                .description(OptionDescription.of(Component.literal("Citizens voice rumors aloud near players.")))
                .binding(true, () -> config.enableRumorTalking, v -> config.enableRumorTalking = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Rumor Talking Chance"))
                .description(OptionDescription.of(Component.literal("Chance (0.0-1.0) a rumor propagation is voiced.")))
                .binding(0.5, () -> config.rumorTalkingChance, v -> config.rumorTalkingChance = v)
                .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 1.0).step(0.05))
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Rumor Talking Range"))
                .description(OptionDescription.of(Component.literal("Max distance to hear voiced rumors.")))
                .binding(12.0, () -> config.rumorTalkingRange, v -> config.rumorTalkingRange = v)
                .controller(opt -> DoubleFieldControllerBuilder.create(opt).range(1.0, 50.0))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Rumors Stored"))
                .description(OptionDescription.of(Component.literal("Max rumors stored per citizen.")))
                .binding(10, () -> config.maxRumorsStored, v -> config.maxRumorsStored = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 100))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Rumors in Prompt"))
                .description(OptionDescription.of(Component.literal("How many rumors to include in a citizen's prompt. 0 = disable.")))
                .binding(3, () -> config.maxRumorsInPrompt, v -> config.maxRumorsInPrompt = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 20))
                .build())
            .build());

        // Broadcast group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Broadcast"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Broadcast"))
                .description(OptionDescription.of(Component.literal("Citizens can broadcast messages across the colony.")))
                .binding(true, () -> config.enableBroadcastPropagation, v -> config.enableBroadcastPropagation = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Broadcast Interval"))
                .description(OptionDescription.of(Component.literal("Ticks between broadcast propagation checks.")))
                .binding(300, () -> config.broadcastPropagationIntervalTicks, v -> config.broadcastPropagationIntervalTicks = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 72000))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Broadcasts per Tick"))
                .description(OptionDescription.of(Component.literal("Max broadcast propagations per tick.")))
                .binding(5, () -> config.broadcastMaxPropagationsPerTick, v -> config.broadcastMaxPropagationsPerTick = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 100))
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Broadcast Range"))
                .description(OptionDescription.of(Component.literal("Max distance for broadcast propagation.")))
                .binding(24.0, () -> config.broadcastPropagationRange, v -> config.broadcastPropagationRange = v)
                .controller(opt -> DoubleFieldControllerBuilder.create(opt).range(1.0, 1000.0))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Broadcasts in Prompt"))
                .description(OptionDescription.of(Component.literal("Recent broadcasts to include in citizen prompts.")))
                .binding(3, () -> config.maxBroadcastsInPrompt, v -> config.maxBroadcastsInPrompt = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, 20))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Max Broadcasts Stored"))
                .description(OptionDescription.of(Component.literal("Max broadcasts stored per citizen.")))
                .binding(20, () -> config.maxBroadcastsStored, v -> config.maxBroadcastsStored = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 100))
                .build())
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Broadcast Yelling"))
                .description(OptionDescription.of(Component.literal("Citizens announce broadcasts aloud near players.")))
                .binding(true, () -> config.enableBroadcastYelling, v -> config.enableBroadcastYelling = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Double>createBuilder()
                .name(Component.literal("Broadcast Yelling Range"))
                .description(OptionDescription.of(Component.literal("Max distance to hear broadcast announcements.")))
                .binding(24.0, () -> config.broadcastYellingRange, v -> config.broadcastYellingRange = v)
                .controller(opt -> DoubleFieldControllerBuilder.create(opt).range(1.0, 10000.0))
                .build())
            .build());

        // Personality group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Personality"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Archetypes"))
                .description(OptionDescription.of(Component.literal("Random personality archetypes influence citizen speech.")))
                .binding(true, () -> config.enablePersonalityArchetypes, v -> config.enablePersonalityArchetypes = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .build());

        // Colony Diplomacy
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Colony Diplomacy"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Diplomacy"))
                .description(OptionDescription.of(Component.literal("Citizens reference neighboring colonies in conversations.")))
                .binding(true, () -> config.enableColonyDiplomacy, v -> config.enableColonyDiplomacy = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .build());

        // Memory group
        cat.group(OptionGroup.createBuilder()
            .name(Component.literal("Memory"))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Memory Compaction"))
                .description(OptionDescription.of(Component.literal("Periodically compact and summarize citizen memories.")))
                .binding(true, () -> config.enableMemoryCompaction, v -> config.enableMemoryCompaction = v)
                .controller(TickBoxControllerBuilder::create)
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Compaction Interval"))
                .description(OptionDescription.of(Component.literal("Ticks between memory compaction checks.")))
                .binding(100, () -> config.memoryCompactionIntervalTicks, v -> config.memoryCompactionIntervalTicks = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(20, 72000))
                .build())
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Compaction Threshold"))
                .description(OptionDescription.of(Component.literal("Events before compaction triggers.")))
                .binding(15, () -> config.memoryCompactionThreshold, v -> config.memoryCompactionThreshold = v)
                .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, 500))
                .build())
            .build());

        return cat.build();
    }

    public static void openPresetCreationScreen(Screen parent) {
        var id = new String[]{""};
        var displayName = new String[]{""};
        var modId = new String[]{"custom"};
        var mode = new PresetDefinition.PresetMode[]{PresetDefinition.PresetMode.PIPELINE};
        var bundledProvider = new String[]{""};
        var sttProvider = new String[]{""};
        var llmProvider = new String[]{""};
        var ttsProvider = new String[]{""};
        var pregenProvider = new String[]{""};
        var providerConfigs = new String[]{"{}"};

        List<String> providerIds = new ArrayList<>(AiRegistry.getProviders().keySet());

        var cat = ConfigCategory.createBuilder()
            .name(Component.literal("Preset Details"));

        // Identity group
        var identityGroup = OptionGroup.createBuilder()
            .name(Component.literal("Identity"))
            .description(OptionDescription.of(Component.literal("Basic preset identification")));

        identityGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Preset ID"))
            .description(OptionDescription.of(Component.literal("Unique local ID (e.g. \"my_preset\"). Full ID becomes <modId>.<id>.")))
            .binding("", () -> id[0], v -> id[0] = v)
            .controller(StringControllerBuilder::create)
            .build());

        identityGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Display Name"))
            .description(OptionDescription.of(Component.literal("Name shown in preset dropdowns (use a translation key to support i18n)")))
            .binding("", () -> displayName[0], v -> displayName[0] = v)
            .controller(StringControllerBuilder::create)
            .build());

        identityGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Source Mod ID"))
            .description(OptionDescription.of(Component.literal("Namespace for the preset (default: custom)")))
            .binding("custom", () -> modId[0], v -> modId[0] = v)
            .controller(StringControllerBuilder::create)
            .build());

        cat.group(identityGroup.build());

        // Mode group
        var modeGroup = OptionGroup.createBuilder()
            .name(Component.literal("Mode"))
            .description(OptionDescription.of(Component.literal("BUNDLED = single provider handles everything. PIPELINE = separate providers per stage.")));

        modeGroup.option(Option.<PresetDefinition.PresetMode>createBuilder()
            .name(Component.literal("Mode"))
            .description(OptionDescription.of(Component.literal("BUNDLED requires a bundled provider. PIPELINE lets you pick per-stage providers.")))
            .binding(PresetDefinition.PresetMode.PIPELINE, () -> mode[0], v -> mode[0] = v)
            .controller(opt -> EnumDropdownControllerBuilder.create(opt))
            .build());

        modeGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Bundled Provider"))
            .description(OptionDescription.of(Component.literal("Provider that handles all stages (only used in BUNDLED mode)")))
            .binding("", () -> bundledProvider[0], v -> bundledProvider[0] = v)
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(providerIds).allowAnyValue(false).allowEmptyValue(true))
            .build());

        cat.group(modeGroup.build());

        // Pipeline providers group
        var pipelineGroup = OptionGroup.createBuilder()
            .name(Component.literal("Pipeline Providers"))
            .description(OptionDescription.of(Component.literal("Individual providers for each stage (PIPELINE mode only)")));

        pipelineGroup.option(Option.<String>createBuilder()
            .name(Component.literal("STT Provider"))
            .description(OptionDescription.of(Component.literal("Speech-to-text provider")))
            .binding("", () -> sttProvider[0], v -> sttProvider[0] = v)
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(providerIds).allowAnyValue(false).allowEmptyValue(true))
            .build());

        pipelineGroup.option(Option.<String>createBuilder()
            .name(Component.literal("LLM Provider"))
            .description(OptionDescription.of(Component.literal("Language model provider")))
            .binding("", () -> llmProvider[0], v -> llmProvider[0] = v)
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(providerIds).allowAnyValue(false).allowEmptyValue(true))
            .build());

        pipelineGroup.option(Option.<String>createBuilder()
            .name(Component.literal("TTS Provider"))
            .description(OptionDescription.of(Component.literal("Text-to-speech provider")))
            .binding("", () -> ttsProvider[0], v -> ttsProvider[0] = v)
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(providerIds).allowAnyValue(false).allowEmptyValue(true))
            .build());

        pipelineGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Pregeneration Provider"))
            .description(OptionDescription.of(Component.literal("Provider for background generation tasks")))
            .binding("", () -> pregenProvider[0], v -> pregenProvider[0] = v)
            .controller(opt -> DropdownStringControllerBuilder.create(opt)
                .values(providerIds).allowAnyValue(false).allowEmptyValue(true))
            .build());

        cat.group(pipelineGroup.build());

        // Advanced group
        var advancedGroup = OptionGroup.createBuilder()
            .name(Component.literal("Advanced"))
            .description(OptionDescription.of(Component.literal("Provider-specific configuration overrides")));

        advancedGroup.option(Option.<String>createBuilder()
            .name(Component.literal("Provider Config JSON"))
            .description(OptionDescription.of(Component.literal("JSON object of provider-specific config overrides (e.g. {\"gemini_live\":{\"temperature\":0.7}})")))
            .binding("{}", () -> providerConfigs[0], v -> providerConfigs[0] = v)
            .controller(StringControllerBuilder::create)
            .build());

        cat.group(advancedGroup.build());

        var screen = YetAnotherConfigLib.createBuilder()
            .title(Component.literal("Create Preset"))
            .category(cat.build())
            .save(() -> {
                if (id[0].isEmpty()) return;
                var config = McTalkingConfig.INSTANCE.instance();

                java.util.Map<String, com.google.gson.JsonElement> pc;
                try {
                    var obj = new com.google.gson.Gson().fromJson(providerConfigs[0], com.google.gson.JsonObject.class);
                    if (obj != null) {
                        var map = new java.util.LinkedHashMap<String, com.google.gson.JsonElement>();
                        for (var e : obj.entrySet()) map.put(e.getKey(), e.getValue());
                        pc = map;
                    } else {
                        pc = Map.of();
                    }
                } catch (Exception e) {
                    pc = Map.of();
                }

                var preset = new PresetDefinition(
                    id[0],
                    displayName[0].isEmpty() ? id[0] : displayName[0],
                    modId[0], mode[0],
                    bundledProvider[0].isEmpty() ? null : bundledProvider[0],
                    sttProvider[0].isEmpty() ? null : sttProvider[0],
                    llmProvider[0].isEmpty() ? null : llmProvider[0],
                    ttsProvider[0].isEmpty() ? null : ttsProvider[0],
                    pregenProvider[0].isEmpty() ? null : pregenProvider[0],
                    pc
                );
                AiRegistry.registerPreset(preset);

                var gson = new com.google.gson.Gson();
                var arr = new com.google.gson.JsonArray();
                try {
                    var existing = gson.fromJson(config.customPresetsJson, com.google.gson.JsonArray.class);
                    if (existing != null) arr = existing;
                } catch (Exception ignored) {}
                arr.add(gson.toJsonTree(preset));
                config.customPresetsJson = gson.toJson(arr);
                McTalkingConfig.INSTANCE.save();
            })
            .build()
            .generateScreen(parent);

        Minecraft.getInstance().setScreen(screen);
    }
}
