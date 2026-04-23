package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * Legacy compatibility facade for citizen prompt helpers.
 * Prefer {@link CitizenPromptService} in new integrations.
 */
public final class CitizenContextUtils {

    private CitizenContextUtils() {
    }

    /**
     * @deprecated Use {@link CitizenPromptService#generateCitizenRoleplayPrompt(me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView)}.
     */
    @Deprecated
    public static String generateCitizenRoleplayPrompt(@NotNull final ICitizenData data, ServerPlayer speakingTo) {
        return CitizenPromptService.generateCitizenRoleplayPrompt(CitizenPromptViewFactory.create(data, speakingTo));
    }

    /**
     * @deprecated Use {@link CitizenPromptService#formatStatus(me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView)}.
     */
    @Deprecated
    public static String formatStatus(VisibleCitizenStatus status, ICitizenData data) {
        return CitizenPromptService.formatStatus(CitizenPromptViewFactory.createStatusView(status, data));
    }
}
