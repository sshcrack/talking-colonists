package me.sshcrack.mc_talking;

import me.sshcrack.mc_talking.platform.Platform;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? neoforge {
/*import me.sshcrack.mc_talking.platform.neoforge.NeoforgePlatform;
		*///?} forge {
import me.sshcrack.mc_talking.platform.forge.ForgePlatform;
 //?}

@SuppressWarnings("LoggingSimilarMessage")
public class ModTemplate {

	public static final String MOD_ID = /*$ mod_id*/ "mc_talking";
	public static final String MOD_VERSION = /*$ mod_version*/ "1.4.2";
	public static final String MOD_FRIENDLY_NAME = /*$ mod_name*/ "MineColonies Talking Citizens";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Platform PLATFORM = createPlatformInstance();

	public static void onInitialize() {
		LOGGER.info("Initializing {} on {}", MOD_ID, ModTemplate.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
	}

	public static void onInitializeClient() {
		LOGGER.info("Initializing {} Client on {}", MOD_ID, ModTemplate.xplat().loader());
		LOGGER.debug("{}: { version: {}; friendly_name: {} }", MOD_ID, MOD_VERSION, MOD_FRIENDLY_NAME);
	}

	static Platform xplat() {
		return PLATFORM;
	}

	private static Platform createPlatformInstance() {
		//? neoforge {
		/*return new NeoforgePlatform();
		*///?} forge {
		return new ForgePlatform();
		 //?}
	}

	private static ResourceLocation id(String path) {
		return id(MOD_ID, path);
	}

	private static ResourceLocation id(String namespace, String path) {
		//? forge {
		return new ResourceLocation(namespace, path);
		//?} neoforge {
		/*return ResourceLocation.fromNamespaceAndPath(namespace, path);
		*///?}
	}
}
