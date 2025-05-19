package me.sshcrack.mc_talking.registry;

import me.sshcrack.mc_talking.MinecoloniesTalkingCitizens;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MinecoloniesTalkingCitizens.MODID);
    
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