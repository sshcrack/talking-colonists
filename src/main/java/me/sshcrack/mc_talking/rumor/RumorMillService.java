package me.sshcrack.mc_talking.rumor;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RumorMillService {
    private RumorMillService() {}

    private static int tickCounter = 0;

    public static void tick(MinecraftServer server) {
        tickCounter++;
        if (!McTalkingConfig.INSTANCE.instance().enableRumorMill) return;

        int maxPropagations = McTalkingConfig.INSTANCE.instance().rumorMillMaxPropagationsPerTick;
        double range = McTalkingConfig.INSTANCE.instance().rumorMillRange;
        double chance = McTalkingConfig.INSTANCE.instance().rumorMillChancePerPair;

        int propagated = 0;
        Set<Long> processedPairs = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var aabb = player.getBoundingBox().inflate(range);
            var citizens = player.level().getEntitiesOfClass(AbstractEntityCitizen.class, aabb);

            if (citizens.size() < 2) continue;

            for (int i = 0; i < citizens.size() && propagated < maxPropagations; i++) {
                AbstractEntityCitizen c1 = citizens.get(i);
                for (int j = i + 1; j < citizens.size() && propagated < maxPropagations; j++) {
                    AbstractEntityCitizen c2 = citizens.get(j);

                    long pairKey = ((long) Math.min(c1.getId(), c2.getId()) << 32)
                            | (Math.max(c1.getId(), c2.getId()) & 0xFFFFFFFFL);
                    if (!processedPairs.add(pairKey)) continue;

                    if (ConversationManager.isCitizenBusy(c1) || ConversationManager.isCitizenBusy(c2)) continue;
                    if (c1.getCitizenData() == null || c2.getCitizenData() == null) continue;

                    if (Math.random() >= chance) continue;

                    if (propagateRumor(c1, c2)) {
                        propagated++;
                        McTalking.LOGGER.info("[RumorMill] {} shared a rumor with {}",
                                c1.getCitizenData().getName(), c2.getCitizenData().getName());
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

        List<String> events = sourceMem.getEvents();
        if (events.isEmpty()) return false;

        String rumor = events.get((int) (Math.random() * events.size()));
        var targetMem = targetData.mc_talking$getOrInitializeMemory();
        targetMem.addEvent(String.format("Rumor: I heard from %s that \"%s\"",
                source.getCitizenData().getName(), rumor));

        return true;
    }
}
