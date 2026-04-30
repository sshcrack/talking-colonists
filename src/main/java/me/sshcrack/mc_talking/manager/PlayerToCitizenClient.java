package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import net.minecraft.server.level.ServerPlayer;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PlayerToCitizenClient extends GeminiWsClient {
    private final ServerPlayer player;

    public PlayerToCitizenClient(AudioProvider audioProvider, AbstractEntityCitizen entity, ServerPlayer player) {
        super(audioProvider, entity);
        this.player = player;
    }

    @Override
    protected String getSystemPrompt() {
        Map<java.util.UUID, String> interestedParties = new HashMap<>();
        interestedParties.put(player.getUUID(), player.getName().getString());

        var promptView = CitizenPromptViewFactory.create(getEntity().getCitizenData(), interestedParties, player);
        return CitizenPromptService.generateCitizenRoleplayPrompt(promptView);
    }

    @Override
    protected void onStreamPause() {
        Objects.requireNonNull(player.getServer()).execute(() -> AiStatusPayload.sendToAll(new AiStatusPayload(getEntity().getUUID(), AiStatus.LISTENING)));
    }

    @Override
    protected void onConversationEnded() {
        Objects.requireNonNull(player.getServer()).execute(() -> AiStatusPayload.sendToAll(new AiStatusPayload(getEntity().getUUID(), AiStatus.LISTENING)));
    }

    @Override
    protected void onGenerationStarted() {
        Objects.requireNonNull(player.getServer()).execute(() -> AiStatusPayload.sendToAll(new AiStatusPayload(getEntity().getUUID(), AiStatus.TALKING)));
    }

    @Override
    protected void onGenerationPaused() {
        Objects.requireNonNull(player.getServer()).execute(() -> AiStatusPayload.sendToAll(new AiStatusPayload(getEntity().getUUID(), AiStatus.LISTENING)));
    }

    @Override
    protected void onQuotaExceededEvent(String message) {
        Objects.requireNonNull(player.getServer()).execute(() -> {
            AiStatusPayload.sendToAll(new AiStatusPayload(getEntity().getUUID(), AiStatus.QUOTA_EXCEEDED));
            if (player.hasPermissions(4)) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        });
    }

    @Override
    protected void onErrorEvent(Exception ex) {
        Objects.requireNonNull(player.getServer()).execute(() -> {
            AiStatusPayload.sendToAll(new AiStatusPayload(getEntity().getUUID(), AiStatus.ERROR));
            if (player.hasPermissions(4) && CONFIG.sendErrorsToPlayers.get()) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("An error occurred in GeminiWsClient: " + ex.getMessage()));
        });
    }
}
