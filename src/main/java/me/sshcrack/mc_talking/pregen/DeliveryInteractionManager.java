package me.sshcrack.mc_talking.pregen;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class DeliveryInteractionManager {
    private DeliveryInteractionManager() {}

    private static final int PREGEN_DISTANCE_SQ = 15 * 15;
    private static final int TICK_INTERVAL = 10;
    private static final long STALE_TIMEOUT_MS = 300_000;
    private static int tickCounter = 0;

    private static final ConcurrentHashMap<String, PendingDelivery> pendingDeliveries = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CachedAudio> pregenCache = new ConcurrentHashMap<>();

    private static class PendingDelivery {
        final IToken<?> token;
        final BlockPos targetPos;
        final int colonyId;
        final ResourceKey<Level> dimension;
        final String itemName;
        final int itemCount;
        final long trackedSince;
        boolean pregenStarted;
        UUID deliverymanUuid;

        PendingDelivery(IToken<?> token, BlockPos targetPos, int colonyId, ResourceKey<Level> dimension,
                        String itemName, int itemCount) {
            this.token = token;
            this.targetPos = targetPos;
            this.colonyId = colonyId;
            this.dimension = dimension;
            this.itemName = itemName;
            this.itemCount = itemCount;
            this.trackedSince = System.currentTimeMillis();
        }
    }

    private static class CachedAudio {
        final AudioChunk audio;
        final UUID speakerUuid;

        CachedAudio(AudioChunk audio, UUID speakerUuid) {
            this.audio = audio;
            this.speakerUuid = speakerUuid;
        }
    }

    public static void trackDelivery(IColony colony, IRequest<?> request, IToken<?> token) {
        if (!McTalkingConfig.INSTANCE.instance().enablePregeneration) return;
        if (!McTalkingConfig.hasGeminiApiKey()) return;
        if (!(request.getRequest() instanceof Delivery delivery)) return;

        String key = token.toString();
        if (pendingDeliveries.containsKey(key)) return;

        BlockPos targetPos = delivery.getTarget().getInDimensionLocation();
        String itemName = delivery.getStack().getHoverName().getString();
        int itemCount = delivery.getStack().getCount();

        PendingDelivery pd = new PendingDelivery(token, targetPos, colony.getID(), colony.getDimension(),
                itemName, itemCount);

        // Find the deliveryman citizen handling this request
        for (ICitizenData cd : colony.getCitizenManager().getCitizens()) {
            if (cd.getJob() instanceof JobDeliveryman dman) {
                IRequest<?> task = dman.getCurrentTask();
                if (task != null && task.getId().equals(token)) {
                    cd.getEntity().ifPresent(entity -> pd.deliverymanUuid = entity.getUUID());
                    break;
                }
            }
        }

        pendingDeliveries.put(key, pd);
        McTalking.LOGGER.info("[DeliveryInteraction] Tracking delivery {} for {} (item: {} x{})",
                key, itemName, itemCount, pd.deliverymanUuid != null ? " found deliveryman" : " no deliveryman yet");
    }

    public static void tick(MinecraftServer server) {
        if (pendingDeliveries.isEmpty()) return;

        tickCounter++;
        if (tickCounter % TICK_INTERVAL != 0) return;

        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingDelivery>> it = pendingDeliveries.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, PendingDelivery> entry = it.next();
            PendingDelivery pd = entry.getValue();

            if (pd.pregenStarted) continue;

            if (now - pd.trackedSince > STALE_TIMEOUT_MS) {
                McTalking.LOGGER.info("[DeliveryInteraction] Removing stale delivery {}", entry.getKey());
                it.remove();
                continue;
            }

            if (pd.deliverymanUuid == null) {
                // Retry finding the deliveryman (may not have been assigned yet)
                tryRetryFindDeliveryman(pd);
                if (pd.deliverymanUuid == null) continue;
            }

            ServerLevel world = server.getLevel(pd.dimension);
            if (world == null) continue;

            Entity raw = world.getEntity(pd.deliverymanUuid);
            if (!(raw instanceof AbstractEntityCitizen deliveryman) || !deliveryman.isAlive()) {
                pd.deliverymanUuid = null;
                continue;
            }

            double distSq = deliveryman.distanceToSqr(
                    pd.targetPos.getX() + 0.5,
                    pd.targetPos.getY() + 0.5,
                    pd.targetPos.getZ() + 0.5);
            if (distSq > PREGEN_DISTANCE_SQ) continue;

            if (!PlayerHeatmapTracker.isPlayerNearby(world,
                    pd.targetPos.getX(), pd.targetPos.getY(), pd.targetPos.getZ(),
                    McTalkingConfig.INSTANCE.instance().pregeneratedGreetingDistance)) {
                continue;
            }

            if (ConversationManager.isCitizenBusy(deliveryman)) continue;

            // Pick speaker and generate prompt
            AbstractEntityCitizen speaker;
            String prompt;

            AbstractEntityCitizen recipient = findNearestCitizen(world, pd.targetPos.getX(), pd.targetPos.getY(), pd.targetPos.getZ());
            if (recipient != null && recipient != deliveryman && recipient.isAlive()
                    && !ConversationManager.isCitizenBusy(recipient)
                    && ThreadLocalRandom.current().nextBoolean()) {
                speaker = recipient;
                prompt = buildRecipientPrompt(recipient, pd);
            } else {
                speaker = deliveryman;
                prompt = buildDeliverymanPrompt(deliveryman, pd);
            }

            if (prompt == null) continue;

            final String finalKey = entry.getKey();
            final UUID speakerUuid = speaker.getUUID();
            pd.pregenStarted = true;

            McTalking.LOGGER.info("[DeliveryInteraction] Starting pregen for delivery {} (speaker: {}, distance: {})",
                    finalKey, speaker.getCitizenData() != null ? speaker.getCitizenData().getName() : "unknown",
                    Math.sqrt(distSq));

            startPregeneration(speaker, prompt, audio -> {
                pregenCache.put(finalKey, new CachedAudio(audio, speakerUuid));
                McTalking.LOGGER.info("[DeliveryInteraction] Pregen complete for {}", finalKey);
            });
        }
    }

    public static void onDeliveryResolved(IColony colony, IRequest<?> request, IToken<?> token) {
        String key = token.toString();
        pendingDeliveries.remove(key);
        CachedAudio cached = pregenCache.remove(key);

        if (cached != null) {
            if (!(colony.getWorld() instanceof ServerLevel world)) return;
            Entity raw = world.getEntity(cached.speakerUuid);
            if (raw instanceof AbstractEntityCitizen speaker && speaker.isAlive()) {
                boolean played = PregenerationPlayback.playAudioIfPossible(speaker, cached.audio);
                if (played) {
                    McTalking.LOGGER.info("[DeliveryInteraction] Played cached audio for delivery {}", key);
                }
            }
        } else if (true) {
            // Fallback: on-demand generation (set to false to test pregen-only path)
            if (!McTalkingConfig.hasGeminiApiKey()) return;
            if (!(colony.getWorld() instanceof ServerLevel world)) return;

            BlockPos targetPos;
            String itemName;
            int itemCount;
            if (request.getRequest() instanceof Delivery delivery) {
                targetPos = delivery.getTarget().getInDimensionLocation();
                itemName = delivery.getStack().getHoverName().getString();
                itemCount = delivery.getStack().getCount();
            } else {
                return;
            }

            if (!PlayerHeatmapTracker.isPlayerNearby(world,
                    targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                    McTalkingConfig.INSTANCE.instance().pregeneratedGreetingDistance)) return;

            AbstractEntityCitizen speaker = findNearestCitizen(world, targetPos.getX(), targetPos.getY(), targetPos.getZ());
            if (speaker == null) return;
            if (ConversationManager.isCitizenBusy(speaker)) return;

            String prompt = "You are " + (speaker.getCitizenData() != null ? speaker.getCitizenData().getName() : "a colonist")
                    + ". React briefly to acknowledge a just-completed delivery of " + itemCount + " " + itemName
                    + " — respond as someone who just received or delivered these items to a fellow colonist.";

            final AbstractEntityCitizen finalSpeaker = speaker;
            startPregeneration(speaker, prompt, audio -> {
                PregenerationPlayback.playAudioIfPossible(finalSpeaker, audio);
            });
        }
    }

    public static void onDeliveryCancelled(IToken<?> token) {
        String key = token.toString();
        pendingDeliveries.remove(key);
        pregenCache.remove(key);
    }

    public static void cleanup() {
        pendingDeliveries.clear();
        pregenCache.clear();
        tickCounter = 0;
    }

    // -------------------------------------------------------------------------
    // Prompt builders
    // -------------------------------------------------------------------------

    private static String buildDeliverymanPrompt(AbstractEntityCitizen deliveryman, PendingDelivery pd) {
        if (deliveryman.getCitizenData() == null) return null;
        String name = deliveryman.getCitizenData().getName();
        return String.format(
                "You are %s, a deliveryman in a Minecraft colony. You just finished a delivery: you brought %d %s to a fellow colonist. React briefly to acknowledge the completed delivery.",
                name, pd.itemCount, pd.itemName);
    }

    @Nullable
    private static String buildRecipientPrompt(AbstractEntityCitizen recipient, PendingDelivery pd) {
        if (recipient.getCitizenData() == null) return null;
        ICitizenData data = recipient.getCitizenData();

        String name = data.getName();
        String job = extractJobName(data);
        if (job == null) job = "colonist";

        StringBuilder sb = new StringBuilder();
        sb.append("You are ").append(name).append(", a ").append(job).append(" in a Minecraft colony.");

        // Include work context for builders
        if (data.getWorkBuilding() != null) {
            IBuilding workBuilding = data.getWorkBuilding();
            if (workBuilding.getBuildingLevel() > 0) {
                sb.append(" You are currently working at ").append(workBuilding.getBuildingDisplayName())
                        .append(" (level ").append(workBuilding.getBuildingLevel()).append(").");
            }
        }

        sb.append(" You just received ").append(pd.itemCount).append(" ").append(pd.itemName)
                .append(" from the deliveryman. React briefly to acknowledge the delivery.");

        return sb.toString();
    }

    @Nullable
    private static String extractJobName(ICitizenData data) {
        if (data.getJob() == null) return null;
        return Component.translatable(data.getJob().getJobRegistryEntry().getTranslationKey()).getString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void tryRetryFindDeliveryman(PendingDelivery pd) {
        IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByDimension(pd.colonyId, pd.dimension);
        if (colony == null) return;

        for (ICitizenData cd : colony.getCitizenManager().getCitizens()) {
            if (cd.getJob() instanceof JobDeliveryman dman) {
                IRequest<?> task = dman.getCurrentTask();
                if (task != null && task.getId().equals(pd.token)) {
                    cd.getEntity().ifPresent(entity -> pd.deliverymanUuid = entity.getUUID());
                    return;
                }
            }
        }
    }

    private static AbstractEntityCitizen findNearestCitizen(ServerLevel world, double x, double y, double z) {
        double range = McTalkingConfig.INSTANCE.instance().mumblingDetectionRange;
        AABB searchBox = new AABB(x - range, y - range, z - range, x + range, y + range, z + range);
        AbstractEntityCitizen nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (AbstractEntityCitizen citizen : world.getEntitiesOfClass(AbstractEntityCitizen.class, searchBox)) {
            if (!citizen.isAlive()) continue;
            double distSq = citizen.distanceToSqr(x, y, z);
            if (distSq < nearestDistSq) {
                nearest = citizen;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    private static void startPregeneration(AbstractEntityCitizen citizen, String prompt, java.util.function.Consumer<AudioChunk> onComplete) {
        if (!ConversationManager.hasFreeCapacity(1)) return;
        if (!ConversationManager.claimSlot(citizen, false)) return;

        PregenerationGeminiClient client = new PregenerationGeminiClient(citizen, prompt, audio -> {
            ConversationManager.releaseSlot(citizen);
            onComplete.accept(audio);
        }, () -> {
            ConversationManager.releaseSlot(citizen);
        });

        try {
            client.connect();
        } catch (Exception e) {
            McTalking.LOGGER.error("[DeliveryInteraction] Failed to connect for citizen {}", citizen.getUUID(), e);
            ConversationManager.releaseSlot(citizen);
        }
    }
}
