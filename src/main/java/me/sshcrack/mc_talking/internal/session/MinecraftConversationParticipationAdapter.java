package me.sshcrack.mc_talking.internal.session;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;

import java.util.Objects;

/**
 * Minecraft-facing adapter for the pure participation module.
 *
 * <p>Provider callbacks may arrive on arbitrary threads. The adapter records the fact immediately,
 * but derives and applies the visible status only on the server thread. Ownership is checked again
 * at application time, so a queued callback from a replaced session cannot overwrite its successor.</p>
 */
public final class MinecraftConversationParticipationAdapter {
    private final AbstractEntityCitizen citizen;
    private final ForegroundSessionRegistry.Token token;
    private final ConversationParticipationModule module;

    public MinecraftConversationParticipationAdapter(
            AbstractEntityCitizen citizen,
            ForegroundSessionRegistry.Token token,
            ConversationParticipationModule module
    ) {
        this.citizen = Objects.requireNonNull(citizen, "citizen");
        this.token = Objects.requireNonNull(token, "token");
        this.module = Objects.requireNonNull(module, "module");
    }

    public void providerConnecting() {
        module.providerConnecting(token);
        refresh();
    }

    public void providerReady() {
        module.providerReady(token);
        refresh();
    }

    public void providerRecovering() {
        module.providerRecovering(token);
        refresh();
    }

    public void playbackThinking() {
        module.playbackThinking(token);
        refresh();
    }

    public void playbackTalking() {
        module.playbackTalking(token);
        refresh();
    }

    public void playbackIdle() {
        module.playbackIdle(token);
        refresh();
    }

    public void urgentWalking(boolean active) {
        module.urgentWalking(token, active);
        refresh();
    }

    public void failure(AiStatus status) {
        module.failure(token, status);
        refresh();
    }

    public void refresh() {
        AiStatusHelper.runOnServerThread(citizen, () ->
                module.presentationIfCurrent(token).ifPresent(status ->
                        AiStatusHelper.setAiStatusOnServerThread(citizen, status)));
    }

    public void complete() {
        module.complete(token);
        AiStatusHelper.runOnServerThread(citizen, () -> {
            if (module.canClearAfterCompletion(token)) {
                AiStatusHelper.setAiStatusOnServerThread(citizen, AiStatus.NONE);
            }
        });
    }
}
