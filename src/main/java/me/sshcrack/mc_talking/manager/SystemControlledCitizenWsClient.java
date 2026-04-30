package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.manager.audio.CitzienEntityAudioProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;

import java.util.HashMap;
import java.util.function.Consumer;

public class SystemControlledCitizenWsClient extends GeminiWsClient {
    private final Consumer<SystemControlledCitizenWsClient> onConversationEnded;

    public SystemControlledCitizenWsClient(AbstractEntityCitizen entity, Consumer<SystemControlledCitizenWsClient> onConversationEnded) {
        super(new CitzienEntityAudioProvider(entity, null), entity);
        this.onConversationEnded = onConversationEnded;
    }

    @Override
    protected void onConversationEnded() {
        onConversationEnded.accept(this);
        super.onConversationEnded();
    }

    @Override
    protected String getSystemPrompt() {
        var view = CitizenPromptViewFactory.create(getEntity().getCitizenData(), new HashMap<>(), null);
        return CitizenPromptService.generateSystemControlledRoleplayPrompt(view);
    }

    @Override
    protected void onQuotaExceededEvent(String message) {
        AiStatusHelper.setAiStatusOnServerThread(getEntity(), AiStatus.QUOTA_EXCEEDED);
    }

    @Override
    protected void onErrorEvent(Exception ex) {
        AiStatusHelper.setAiStatusOnServerThread(getEntity(), AiStatus.ERROR);
    }
}
