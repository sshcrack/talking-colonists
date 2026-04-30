package me.sshcrack.mc_talking.duck;

import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;

public interface CitizenDataMemoryExtended {
    CitizenMemories mc_talking$getMemory();

    CitizenMemories mc_talking$getOrInitializeMemory();
}
