package me.sshcrack.mc_talking;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

@Mod(McTalking.MODID)
public class McTalkingDedicated {

    public McTalkingDedicated(IEventBus modBus) {
        McTalking.isDedicated = true;
    }
}
