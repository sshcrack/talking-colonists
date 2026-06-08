package me.sshcrack.mc_talking.duck;

import me.sshcrack.mc_talking.util.ColonyEventBuffer;

import java.util.concurrent.ConcurrentLinkedDeque;

public interface ColonyEventDataProvider {
    ConcurrentLinkedDeque<ColonyEventBuffer.ColonyEvent> mc_talking$getOrCreateEvents();

    long mc_talking$getLastRaidEndTime();

    void mc_talking$setLastRaidEndTime(long time);

    int mc_talking$getLastRaidLostCitizens();

    void mc_talking$setLastRaidLostCitizens(int count);
}
