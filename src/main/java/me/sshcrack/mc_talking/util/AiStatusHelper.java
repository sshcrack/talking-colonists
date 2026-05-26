package me.sshcrack.mc_talking.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.duck.AbstractEntityCitizenAiStatusProvider;
import me.sshcrack.mc_talking.manager.music.MusicManager;
import me.sshcrack.mc_talking.network.AiStatus;

public class AiStatusHelper {
    private AiStatusHelper() {

    }

    public static void setAiStatusSynced(AbstractEntityCitizen citizen, AiStatus status) {
        citizen.level().getServer().execute(() -> AiStatusHelper.setAiStatusOnServerThread(citizen, status));
    }

    public static void setAiStatusOnServerThread(AbstractEntityCitizen citizen, AiStatus status) {
        ((AbstractEntityCitizenAiStatusProvider) citizen).mc_talking$setStatus(status);

        try {
            MusicManager musicManager = MusicManager.getInstance();
            if (status == AiStatus.TALKING) {
                musicManager.duckMusicForEntity(citizen.getUUID());
            } else {
                musicManager.restoreMusicForEntity(citizen.getUUID());
            }
        } catch (IllegalStateException ignored) {
            // MusicManager not initialized; music disabled
        }
    }
}
