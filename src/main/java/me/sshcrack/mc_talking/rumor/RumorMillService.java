package me.sshcrack.mc_talking.rumor;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class RumorMillService {
    private RumorMillService() {}

    public static void tick(MinecraftServer server) {
        var cfg = McTalkingConfig.INSTANCE.instance();
        if (!cfg.enableRumorMill) return;

        int maxPropagations = cfg.rumorMillMaxPropagationsPerTick;
        double range = cfg.rumorMillRange;
        double chance = cfg.rumorMillChancePerPair;

        int propagated = 0;
        Set<Long> processedPairs = new HashSet<>();

        for (ServerLevel level : server.getAllLevels()) {
            if (propagated >= maxPropagations) break;

            for (IColony colony : IMinecoloniesAPI.getInstance().getColonyManager().getColonies(level)) {
                if (propagated >= maxPropagations) break;

                List<ICitizenData> allCitizens = new ArrayList<>(colony.getCitizenManager().getCitizens());
                if (allCitizens.size() < 2) continue;

                List<AbstractEntityCitizen> entities = new ArrayList<>();
                for (ICitizenData data : allCitizens) {
                    var entityOpt = data.getEntity();
                    if (entityOpt.isPresent() && entityOpt.get().isAlive()) {
                        entities.add(entityOpt.get());
                    }
                }

                for (int i = 0; i < entities.size() && propagated < maxPropagations; i++) {
                    AbstractEntityCitizen c1 = entities.get(i);
                    for (int j = i + 1; j < entities.size() && propagated < maxPropagations; j++) {
                        AbstractEntityCitizen c2 = entities.get(j);

                        if (c1.distanceToSqr(c2) > range * range) continue;

                        long pairKey = ((long) Math.min(c1.getId(), c2.getId()) << 32)
                                | (Math.max(c1.getId(), c2.getId()) & 0xFFFFFFFFL);
                        if (!processedPairs.add(pairKey)) continue;

                        if (ConversationManager.isCitizenBusy(c1) || ConversationManager.isCitizenBusy(c2)) continue;
                        if (c1.getCitizenData() == null || c2.getCitizenData() == null) continue;

                        if (ThreadLocalRandom.current().nextDouble() >= chance) continue;

                        if (propagated < maxPropagations && propagateRumor(c1, c2)) {
                            propagated++;
                            McTalking.LOGGER.info("[RumorMill] {} shared a rumor with {}",
                                    c1.getCitizenData().getName(), c2.getCitizenData().getName());
                        }

                        if (propagated < maxPropagations && propagateRumor(c2, c1)) {
                            propagated++;
                            McTalking.LOGGER.info("[RumorMill] {} shared a rumor with {}",
                                    c2.getCitizenData().getName(), c1.getCitizenData().getName());
                        }
                    }
                }
            }
        }
    }

    private static boolean propagateRumor(AbstractEntityCitizen source, AbstractEntityCitizen target) {
        var sourceData = (CitizenDataMemoryExtended) source.getCitizenData();
        var targetData = (CitizenDataMemoryExtended) target.getCitizenData();

        var sourceMem = sourceData.mc_talking$getMemory();
        if (sourceMem == null) return false;

        String content = null;

        if (sourceMem.hasPendingRumor()) {
            content = sourceMem.drainPendingRumor();
        } else {
            List<String> events = sourceMem.getEvents();
            if (events.isEmpty()) return false;

            List<String> firstHand = new ArrayList<>();
            for (String e : events) {
                if (!e.startsWith("Rumor:")) {
                    firstHand.add(e);
                }
            }
            if (firstHand.isEmpty()) return false;

            content = firstHand.get(ThreadLocalRandom.current().nextInt(firstHand.size()));
        }

        var targetMem = targetData.mc_talking$getOrInitializeMemory();
        targetMem.addEvent(String.format("Rumor: I heard from %s that \"%s\"",
                source.getCitizenData().getName(), content));

        return true;
    }
}
