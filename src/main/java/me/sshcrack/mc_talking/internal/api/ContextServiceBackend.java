package me.sshcrack.mc_talking.internal.api;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.service.ContextService;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class ContextServiceBackend implements ContextService {
    @Override
    public @NotNull CitizenPromptView snapshot(@NotNull AbstractEntityCitizen citizen,
                                                @Nullable ServerPlayer speakingPlayer) {
        var data = citizen.getCitizenData();
        if (data == null) throw new IllegalStateException("Citizen data is not available");
        return CitizenPromptViewFactory.create(data, Map.of(), speakingPlayer);
    }
}
