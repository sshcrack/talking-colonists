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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.jetbrains.annotations.Nullable;

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

                        if (propagated < maxPropagations) {
                            Rumor rumor = propagateRumor(c1, c2);
                            if (rumor != null) {
                                propagated++;
                                McTalking.LOGGER.info("[RumorMill] {} shared a rumor with {}",
                                        c1.getCitizenData().getName(), c2.getCitizenData().getName());
                                attemptRumorTalking(c1, c2, rumor, server, cfg);
                            }
                        }

                        if (propagated < maxPropagations) {
                            Rumor rumor = propagateRumor(c2, c1);
                            if (rumor != null) {
                                propagated++;
                                McTalking.LOGGER.info("[RumorMill] {} shared a rumor with {}",
                                        c2.getCitizenData().getName(), c1.getCitizenData().getName());
                                attemptRumorTalking(c2, c1, rumor, server, cfg);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void attemptRumorTalking(AbstractEntityCitizen source, AbstractEntityCitizen target, Rumor rumor, MinecraftServer server, McTalkingConfig cfg) {
        if (!cfg.enableRumorTalking) return;
        if (ThreadLocalRandom.current().nextDouble() >= cfg.rumorTalkingChance) return;
        if (!ConversationManager.hasPlayerNearby(source, server, cfg.rumorTalkingRange)) return;

        String targetName    = target.getCitizenData().getName();
        String originatorName = rumor.getOriginatorName();
        String content       = rumor.getContent();

        String prompt = String.format(
                "## CURRENT TASK\n" +
                "Turn to %s and tell them a piece of news you heard. " +
                "You originally heard it from %s: \"%s\". " +
                "Say it naturally in character — keep it to one or two sentences. " +
                "Do not repeat the source attribution verbatim; weave it into conversation.",
                targetName, originatorName, content
        );

        ConversationManager.startLowPrioritySession(source, prompt);
    }

    @Nullable
    private static Rumor propagateRumor(AbstractEntityCitizen source, AbstractEntityCitizen target) {
        var sourceData = (CitizenDataMemoryExtended) source.getCitizenData();
        var targetData = (CitizenDataMemoryExtended) target.getCitizenData();

        var sourceMem = sourceData.mc_talking$getMemory();
        if (sourceMem == null) return null;

        var targetMem = targetData.mc_talking$getOrInitializeMemory();

        // Try to share a rumor the target hasn't heard yet.
        for (Rumor r : sourceMem.getReceivedRumors()) {
            if (!targetMem.hasHeardRumor(r.getId())) {
                targetMem.addRumor(r);
                return r;
            }
        }

        // No unheard rumors — promote a first-hand event to a new rumor.
        List<String> firstHandEvents = sourceMem.getEvents().stream()
                .filter(e -> !e.startsWith("Rumor:"))
                .toList();
        if (firstHandEvents.isEmpty()) return null;

        String content = firstHandEvents.get(ThreadLocalRandom.current().nextInt(firstHandEvents.size()));
        String originatorName = source.getCitizenData().getName();
        String id = UUID.randomUUID().toString();

        Rumor newRumor = new Rumor(id, originatorName, content);
        sourceMem.addRumor(newRumor);
        targetMem.addRumor(newRumor);
        return newRumor;
    }
}
