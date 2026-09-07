package me.sshcrack.mc_talking.api.service;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Runtime service for immutable normalized citizen snapshots. */
public interface ContextService {
    @NotNull CitizenPromptView snapshot(@NotNull AbstractEntityCitizen citizen, @Nullable ServerPlayer speakingPlayer);
}
