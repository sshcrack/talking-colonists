package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public class PlayerToCitizenClient extends GeminiWsClient {
    private final ServerPlayer player;

    public PlayerToCitizenClient(TalkingManager manager, ServerPlayer player) {
        super(manager);
        this.player = player;
    }

    @Override
    protected String getSystemPrompt() {
        var promptView = CitizenPromptViewFactory.create(getTalkingManager().entity.getCitizenData(), interestedParties, initialPlayer);
        return CitizenPromptService.generateCitizenRoleplayPrompt(promptView);
    }

    @Override
    protected void onStreamPause() {
        Objects.requireNonNull(player.getServer()).execute(() -> AiStatusPayload.sendToAll(new AiStatusPayload(manager.entity.getUUID(), AiStatus.LISTENING)))
    }
}
