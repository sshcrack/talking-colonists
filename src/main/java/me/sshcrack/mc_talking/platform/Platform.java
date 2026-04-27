package me.sshcrack.mc_talking.platform;

/*? if neoforge{*/

import me.sshcrack.mc_talking.platform.neoforge.NeoforgePlatformImpl;
/*?}*/

public abstract class Platform {
	private static final Platform cachedPlatform;

	public static Platform get() {
		return cachedPlatform;
	}

	static {
		/*? if forge {*/
		/* cachedPlatform = new ForgePlatformImpl(); */
		/*? }*/
		/*? if neoforge{*/
		cachedPlatform = new NeoforgePlatformImpl();
		/*?}*/
	}
}
