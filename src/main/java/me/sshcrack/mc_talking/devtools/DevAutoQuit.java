/*? if devtools {*/
package me.sshcrack.mc_talking.devtools;

import net.minecraft.client.Minecraft;
/*? if neoforge {*/
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
/*?}*/
/*? if forge {*/
/*import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
*//*?}*/

/*? if forge {*/
/*@Mod.EventBusSubscriber(modid = "mc_talking", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
*//*?}*/
public class DevAutoQuit {
    private static boolean quitting = false;
    private static int ticksSinceLoad = 0;
    private static final int QUIT_DELAY_TICKS = 60;

    /*? if neoforge {*/
    public static void init() {
        if (!isEnabled()) return;
        NeoForge.EVENT_BUS.register(new DevAutoQuit());
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        tick();
    }
    /*?}*/

    /*? if forge {*/
    /*@SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick();
    }
    *//*?}*/

    private static boolean isEnabled() {
        return "true".equals(System.getProperty("mc_talking.autoQuit"));
    }

    private static void tick() {
        if (quitting || !isEnabled()) return;

        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ticksSinceLoad = 0;
            return;
        }

        ticksSinceLoad++;
        if (ticksSinceLoad >= QUIT_DELAY_TICKS) {
            quitting = true;
            mc.execute(mc::stop);
        }
    }
}
/*?}*/
