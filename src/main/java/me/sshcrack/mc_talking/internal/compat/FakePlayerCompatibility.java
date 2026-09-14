package me.sshcrack.mc_talking.internal.compat;

import net.minecraft.server.level.ServerPlayer;
/*? if forge {*/
/*import net.minecraftforge.common.util.FakePlayer;
*//*?}*/
/*? if neoforge {*/
import net.neoforged.neoforge.common.util.FakePlayer;
/*?}*/
import org.jetbrains.annotations.NotNull;

/** Loader-neutral recognition of server-side fake players used by addon automation/test harnesses. */
public final class FakePlayerCompatibility {
    private FakePlayerCompatibility() {
    }

    public static boolean isFakePlayer(@NotNull ServerPlayer player) {
        return player instanceof FakePlayer;
    }
}
