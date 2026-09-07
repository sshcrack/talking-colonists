package me.sshcrack.mc_talking.api.conversation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** Fixed spatial-audio origin for a controlled turn, such as a podium microphone. */
public record ControlledAudioAnchor(
        @NotNull ResourceKey<Level> dimension,
        double x,
        double y,
        double z
) {
    public ControlledAudioAnchor {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("audio anchor coordinates must be finite");
        }
    }

    public static @NotNull ControlledAudioAnchor at(
            @NotNull ResourceKey<Level> dimension,
            @NotNull Vec3 position
    ) {
        return new ControlledAudioAnchor(dimension, position.x, position.y, position.z);
    }
}
