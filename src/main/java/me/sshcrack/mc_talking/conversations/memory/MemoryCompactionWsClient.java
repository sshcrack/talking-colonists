package me.sshcrack.mc_talking.conversations.memory;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;

import java.util.function.Consumer;

/** Memory compaction through the cheap Live model (memoryMode LIVE). */
public class MemoryCompactionWsClient extends LiveTextClient {
    public MemoryCompactionWsClient(AbstractEntityCitizen citizen, String prompt,
                                    Consumer<String> onComplete, Runnable onError) {
        super(MemoryCompactionService.SYSTEM_PROMPT, prompt,
                citizen.getCitizenData() == null ? null : citizen.getUUID(),
                citizen.getCitizenData() != null && citizen.getCitizenData().isFemale(),
                "[MemoryCompaction]", onComplete, onError);
    }
}
