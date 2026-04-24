package me.sshcrack.mc_talking.registry;

import com.minecolonies.api.creativetab.ModCreativeTabs;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import java.util.function.Supplier;
/*? if forge {*/
/*import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;*/
/*?}*/
/*? if neoforge {*/
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
/*?}*/

public class ModItems {
    /*? if forge {*/
    /*public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, McTalking.MODID);*/
    /*?}*/

    /*? if neoforge {*/
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, McTalking.MODID);
    /*?}*/

    public static final Supplier<Item> TALKING_DEVICE = ITEMS.register("talking_device", CitizenTalkingDevice::new);

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModCreativeTabs.GENERAL.getKey()) {
            /*? if forge {*/
            /*event.accept(TALKING_DEVICE.get());*/
            /*?}*/
            /*? if neoforge {*/
            event.accept(TALKING_DEVICE.get(), net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            /*?}*/
        }
    }
}
