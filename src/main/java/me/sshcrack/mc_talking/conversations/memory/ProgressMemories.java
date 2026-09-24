package me.sshcrack.mc_talking.conversations.memory;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.memory.MemoryProvenance;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Citizens notice when things get better for them: moving into a home, being given work, getting
 * well. Each improvement becomes a memory, so they bring it up with gratitude instead of only ever
 * voicing problems. Only changes seen while the server runs count; the first check after a start
 * just records how everyone is doing. Server thread only.
 */
public final class ProgressMemories {
    /** What matters to a citizen's everyday life, as last seen. */
    record Situation(boolean housed, @Nullable String job, boolean sick) {
    }

    private static final Map<UUID, Situation> LAST = new HashMap<>();

    private ProgressMemories() {
    }

    public static void tick(MinecraftServer server) {
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            for (ICitizenData data : colony.getCitizenManager().getCitizens()) {
                UUID id = data.getUUID();
                Situation now = situation(data);
                Situation before = LAST.put(id, now);
                if (before == null) continue;
                for (String improvement : improvements(before, now, colony.getDay())) {
                    remember(data, improvement);
                }
            }
        }
    }

    /** The memories for what got better between two checks; empty when nothing did. */
    static List<String> improvements(Situation before, Situation now, int day) {
        List<String> memories = new ArrayList<>();
        if (!before.housed() && now.housed()) {
            memories.add("Moved into a home of your own on day " + day + ", and felt grateful and relieved to finally have a place to live.");
        }
        if (before.job() == null && now.job() != null) {
            memories.add("Was given work as " + now.job() + " on day " + day + ", and was glad to be useful to the colony.");
        } else if (before.job() != null && now.job() != null && !before.job().equals(now.job())) {
            memories.add("Started a new job as " + now.job() + " on day " + day + ".");
        }
        if (before.sick() && !now.sick()) {
            memories.add("Recovered from an illness on day " + day + ", and was thankful to feel well again.");
        }
        return memories;
    }

    private static Situation situation(ICitizenData data) {
        String job = null;
        try {
            if (data.getJob() != null) job = data.getJob().getJobRegistryEntry().getKey().getPath().replace('_', ' ');
        } catch (RuntimeException ignored) {
            // an unregistered job counts as none
        }
        return new Situation(data.getHomeBuilding() != null, job, data.getCitizenDiseaseHandler().isSick());
    }

    private static void remember(ICitizenData data, String event) {
        if (!(data instanceof CitizenDataMemoryExtended extended)) return;
        CitizenMemories memories = extended.mc_talking$getOrInitializeMemory();
        if (memories == null) return;
        memories.addEvent(event, MemoryProvenance.OBSERVED_EVENT, null, McTalking.MODID + ":progress", null);
        McTalking.LOGGER.debug("[ProgressMemories] {}: {}", data.getName(), event);
    }

    public static void clear() {
        LAST.clear();
    }
}
