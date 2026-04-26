package me.sshcrack.mc_talking.platform.neoforge;

//? neoforge {

import me.sshcrack.mc_talking.ModTemplate;
import net.neoforged.fml.common.Mod;

@Mod(ModTemplate.MOD_ID)
public class NeoforgeEntrypoint {

	public NeoforgeEntrypoint() {
		ModTemplate.onInitialize();
	}
}
//?}
