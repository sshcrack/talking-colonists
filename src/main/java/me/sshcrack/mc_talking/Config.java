package me.sshcrack.mc_talking;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
@EventBusSubscriber(modid = MinecoloniesTalkingCitizens.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> GEMINI_API_KEY = BUILDER
            .worldRestart()
            .comment("This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey")
            .define("gemini_key", "");

    public static final ModConfigSpec.ConfigValue<Boolean> RESPOND_IN_GROUPS = BUILDER
            .comment("Wheather the citizens should respond if the player is in a group or not.")
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


    public static final ModConfigSpec.ConfigValue<Integer> MAX_CONCURRENT_AGENTS = BUILDER
            .comment("Maximum number of AI agents that can be activated at once")
            .define("max_concurrent_agents", 30, e -> (int) e > 0);


    static final ModConfigSpec SPEC = BUILDER
            .build();

    public static String geminiApiKey;
    public static int maxConcurrentAgents;
    public static boolean respondInGroups;
    public static double activationDistance;
    public static int lookDurationTicks;
    public static int lookToleranceMs;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        geminiApiKey = GEMINI_API_KEY.get();
        respondInGroups = RESPOND_IN_GROUPS.get();
        activationDistance = ACTIVATION_DISTANCE.get();
        lookDurationTicks = LOOK_DURATION_TICKS.get();
        lookToleranceMs = LOOK_TOLERANCE_MS.get();
        maxConcurrentAgents = MAX_CONCURRENT_AGENTS.get();
    }
}
