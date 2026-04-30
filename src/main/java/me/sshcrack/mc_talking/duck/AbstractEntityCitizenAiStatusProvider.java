package me.sshcrack.mc_talking.duck;

import me.sshcrack.mc_talking.network.AiStatus;

public interface AbstractEntityCitizenAiStatusProvider {
    AiStatus mc_talking$getAiStatus();

    void mc_talking$setStatus(AiStatus status);

    boolean mc_talking$isStatusDirty();

    void mc_talking$markStatusClean();
}
