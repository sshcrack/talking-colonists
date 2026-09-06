/*? if devtools {*/
/*package me.sshcrack.mc_talking.devtools;

import me.sshcrack.mc_talking.McTalking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
/^? if neoforge {^/
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
/^?}^/
/^? if forge {^/
/^import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
^//^?}^/

/^? if forge {^/
/^@Mod.EventBusSubscriber(modid = "mc_talking", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
^//^?}^/
public class DevAutoQuit {
    private static boolean quitting = false;
    private static boolean worldCreationRequested = false;
    private static boolean worldCreationConfirmed = false;
    private static int ticksSinceStart = 0;
    private static int ticksInWorld = 0;
    private static final int QUIT_DELAY_TICKS = 60;
    private static final int STARTUP_TIMEOUT_TICKS = 20 * 90;
    private static final int ENTER_KEY = 257;

    /^? if neoforge {^/
    public static void init() {
        if (!isEnabled()) return;
        NeoForge.EVENT_BUS.register(new DevAutoQuit());
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        tick();
    }
    /^?}^/

    /^? if forge {^/
    /^@SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick();
    }
    ^//^?}^/

    private static boolean isEnabled() {
        return "true".equals(System.getProperty("mc_talking.autoQuit"));
    }

    private static void tick() {
        if (quitting || !isEnabled()) return;

        ticksSinceStart++;
        var mc = Minecraft.getInstance();
        boolean worldReady = mc.level != null && mc.player != null;

        if (worldReady) {
            ticksInWorld++;
            if (ticksInWorld == 1) {
                McTalking.LOGGER.info("MC_TALKING_AUTOQUIT_READY:world");
            }
            if (ticksInWorld >= QUIT_DELAY_TICKS) {
                quitting = true;
                McTalking.LOGGER.info("MC_TALKING_AUTOQUIT_SUCCESS:world");
                mc.execute(mc::stop);
            }
            return;
        }

        ticksInWorld = 0;

        if (mc.screen instanceof AccessibilityOnboardingScreen onboardingScreen) {
            McTalking.LOGGER.info("MC_TALKING_AUTOQUIT_ONBOARDING: continuing to title screen");
            onboardingScreen.onClose();
            return;
        }

        if (!worldCreationRequested && mc.screen instanceof TitleScreen titleScreen) {
            worldCreationRequested = true;
            McTalking.LOGGER.info("MC_TALKING_AUTOQUIT_CREATE_WORLD: opening vanilla create-world screen");
            CreateWorldScreen.openFresh(mc, titleScreen);
            return;
        }

        if (worldCreationRequested && !worldCreationConfirmed && mc.screen instanceof CreateWorldScreen createWorldScreen) {
            worldCreationConfirmed = true;
            createWorldScreen.getUiState().setName("MC_Talking_Smoke");
            McTalking.LOGGER.info("MC_TALKING_AUTOQUIT_CREATE_WORLD: creating MC_Talking_Smoke");
            createWorldScreen.keyPressed(ENTER_KEY, 0, 0);
            return;
        }

        if (ticksSinceStart >= STARTUP_TIMEOUT_TICKS) {
            quitting = true;
            String screen = mc.screen == null ? "none" : mc.screen.getClass().getName();
            McTalking.LOGGER.error("MC_TALKING_AUTOQUIT_FAILURE: client never entered a world (screen={})", screen);
            mc.execute(mc::stop);
        }
    }
}
*//*?}*/
