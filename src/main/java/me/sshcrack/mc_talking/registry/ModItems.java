package me.sshcrack.mc_talking.registry;

import com.minecolonies.api.creativetab.ModCreativeTabs;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, McTalking.MODID);

    // Register items
    public static final RegistryObject<Item> TALKING_DEVICE = ITEMS.register("talking_device",
            CitizenTalkingDevice::new);

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);

        // Add to creative tab
        modEventBus.addListener(ModItems::onBuildCreativeTabs);
    }    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModCreativeTabs.GENERAL.getKey()) {
            event.accept(TALKING_DEVICE.get());
        }
    }
}
