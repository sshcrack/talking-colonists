package me.sshcrack.mc_talking.broadcast;

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

public class BroadcastPropagationService {
    private BroadcastPropagationService() {}

    public static void tick(MinecraftServer server) {
        var cfg = McTalkingConfig.INSTANCE.instance();
        if (!cfg.enableBroadcastPropagation) return;

        int propagationsLeft = cfg.broadcastMaxPropagationsPerTick;
        double rangeSqr = cfg.broadcastPropagationRange * cfg.broadcastPropagationRange;

        for (ServerLevel level : server.getAllLevels()) {
            if (propagationsLeft <= 0) break;

            for (IColony colony : IMinecoloniesAPI.getInstance().getColonyManager().getColonies(level)) {
                if (propagationsLeft <= 0) break;

                List<ICitizenData> allCitizens = new ArrayList<>(colony.getCitizenManager().getCitizens());
                if (allCitizens.size() < 2) continue;

                List<AbstractEntityCitizen> entities = new ArrayList<>();
                for (ICitizenData data : allCitizens) {
                    var entityOpt = data.getEntity();
                    if (entityOpt.isPresent() && entityOpt.get().isAlive()) {
                        entities.add(entityOpt.get());
                    }
                }
                if (entities.size() < 2) continue;

                Set<Long> processedPairs = new HashSet<>();

                for (int i = 0; i < entities.size() && propagationsLeft > 0; i++) {
                    AbstractEntityCitizen carrierEntity = entities.get(i);
                    ICitizenData carrier = carrierEntity.getCitizenData();
                    if (carrier == null) continue;

                    CitizenMemories carrierMem = ((CitizenDataMemoryExtended) carrier).mc_talking$getMemory();
                    if (carrierMem == null) continue;
                    if (carrierMem.getReceivedBroadcasts().isEmpty()) continue;

                    for (int j = 0; j < entities.size() && propagationsLeft > 0; j++) {
                        if (i == j) continue;
                        AbstractEntityCitizen recipientEntity = entities.get(j);

                        if (carrierEntity.distanceToSqr(recipientEntity) > rangeSqr) continue;

                        long pairKey = ((long) Math.min(carrierEntity.getId(), recipientEntity.getId()) << 32)
                                | (Math.max(carrierEntity.getId(), recipientEntity.getId()) & 0xFFFFFFFFL);
                        if (!processedPairs.add(pairKey)) continue;

                        ICitizenData recipient = recipientEntity.getCitizenData();
                        if (recipient == null) continue;

                        CitizenMemories recipientMem = ((CitizenDataMemoryExtended) recipient).mc_talking$getOrInitializeMemory();

                        ColonyBroadcast firstShared = null;
                        for (ColonyBroadcast broadcast : carrierMem.getReceivedBroadcasts()) {
                            if (recipientMem.hasHeardBroadcast(broadcast.getId())) continue;
                            recipientMem.addBroadcast(broadcast);
                            if (firstShared == null) firstShared = broadcast;
                        }

                        if (firstShared != null) {
                            McTalking.LOGGER.info("[Broadcast] {} → {}: shared broadcasts",
                                    carrier.getName(), recipient.getName());
                            propagationsLeft--;

                            if (cfg.enableBroadcastYelling) {
                                if (ConversationManager.hasPlayerNearby(carrierEntity, server, cfg.broadcastYellingRange)
                                        && !ConversationManager.isCitizenBusy(carrierEntity)) {
                                    String prompt = "A message has arrived from "
                                            + firstShared.getSenderPlayerName()
                                            + " for the colony: ["
                                            + firstShared.getMessage()
                                            + "]. Spread the word to those nearby. Don't mention obstacles or anything blocking you.";
                                    ConversationManager.startLowPrioritySession(carrierEntity, prompt);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
