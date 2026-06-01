package me.sshcrack.mc_talking.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.duck.AbstractEntityCitizenAiStatusProvider;
import me.sshcrack.mc_talking.network.AiStatus;

public class AiStatusHelper {
    private AiStatusHelper() {

    }

    public static void setAiStatusSynced(AbstractEntityCitizen citizen, AiStatus status) {
        var level = citizen.level();
        if (level == null) return;
        var server = level.getServer();
        if (server == null) return;
        var serverThread = server.getRunningThread();
        if (Thread.currentThread() == serverThread) {
            AiStatusHelper.setAiStatusOnServerThread(citizen, status);
        } else {
            server.execute(() -> AiStatusHelper.setAiStatusOnServerThread(citizen, status));
        }
    }

    public static void setAiStatusOnServerThread(AbstractEntityCitizen citizen, AiStatus status) {
        ((AbstractEntityCitizenAiStatusProvider) citizen).mc_talking$setStatus(status);
    }
}
