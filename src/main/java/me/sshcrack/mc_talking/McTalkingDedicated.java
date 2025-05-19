package me.sshcrack.mc_talking;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = MinecoloniesTalkingCitizens.MODID, dist = Dist.DEDICATED_SERVER)
public class McTalkingDedicated {

    public McTalkingDedicated(IEventBus modBus) {
        MinecoloniesTalkingCitizens.isDedicated = true;
    }
}
