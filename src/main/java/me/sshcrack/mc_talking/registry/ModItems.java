package me.sshcrack.mc_talking.registry;

import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredHolder;
import net.minecraftforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, McTalking.MODID);

    // Register items
    public static final DeferredHolder<Item, Item> TALKING_DEVICE = ITEMS.register("talking_device",
            CitizenTalkingDevice::new);

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);

        // Add to creative tab
        modEventBus.addListener(ModItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(TALKING_DEVICE.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
