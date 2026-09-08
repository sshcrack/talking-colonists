package me.sshcrack.mc_talking.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.duck.AbstractEntityCitizenAiStatusProvider;
import me.sshcrack.mc_talking.network.AiStatus;

public class AiStatusHelper {
    private AiStatusHelper() {

    }

    public static void setAiStatusSynced(AbstractEntityCitizen citizen, AiStatus status) {
        runOnServerThread(citizen, () -> AiStatusHelper.setAiStatusOnServerThread(citizen, status));
    }

    /**
     * Runs an AI-presentation action on the owning Minecraft server thread.
     * Ownership-sensitive callers must perform their ownership check inside {@code action}, not
     * before queueing it, so delayed callbacks cannot apply stale presentation.
     */
    public static void runOnServerThread(AbstractEntityCitizen citizen, Runnable action) {
        var level = citizen.level();
        var server = level.getServer();
        if (server == null) return;
        var serverThread = server.getRunningThread();
        if (Thread.currentThread() == serverThread) {
            action.run();
        } else {
            server.execute(action);
        }
    }

    public static void setAiStatusOnServerThread(AbstractEntityCitizen citizen, AiStatus status) {
        ((AbstractEntityCitizenAiStatusProvider) citizen).mc_talking$setStatus(status);
    }
}
