package me.sshcrack.mc_talking;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

@Mod(value = McTalking.MODID, dist = Dist.DEDICATED_SERVER)
public class McTalkingDedicated {

    public McTalkingDedicated(IEventBus modBus) {
        McTalking.isDedicated = true;
    }
}
