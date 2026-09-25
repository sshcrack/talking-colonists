package me.sshcrack.mc_talking.conversations.complaints;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.config.PersonalityArchetype;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.duck.CitizenDataPersonalityExtended;
import me.sshcrack.mc_talking.manager.prompt.CitizenWorkViews;
import me.sshcrack.mc_talking.util.ComplaintRamp;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Connects {@link ComplaintHistory} to the game: which problems a citizen has right now, whether a fix
 * is visibly under way, the personality's pace, and recording raised problems and replies.
 */
public final class Complaints {
    private static final String RESIDENCE = "residence";

    private Complaints() {
    }

    public static @Nullable ComplaintHistory history(@Nullable ICitizenData data) {
        if (!(data instanceof CitizenDataMemoryExtended extended)) return null;
        CitizenMemories memories = extended.mc_talking$getOrInitializeMemory();
        return memories == null ? null : memories.complaints();
    }

    /** The problems the citizen has right now, from MineColonies' happiness modifiers. */
    public static Set<ComplaintTopic> activeTopics(ICitizenData data) {
        Set<ComplaintTopic> topics = EnumSet.noneOf(ComplaintTopic.class);
        boolean guard = data.getJob() != null && data.getJob().isGuard();
        for (HappinessModifierView modifier : CitizenWorkViews.extractHappinessModifiers(data)) {
            if (modifier.factor() >= ComplaintRamp.NEGATIVE_FACTOR) continue;
            if (modifier.type() == HappinessModifierType.HOMELESSNESS && guard) continue;
            ComplaintTopic topic = ComplaintTopic.of(modifier.type());
            if (topic != null) topics.add(topic);
        }
        if (data.getCitizenDiseaseHandler().isSick()) topics.add(ComplaintTopic.HEALTH);
        if (data.getJobStatus() == JobStatus.STUCK) topics.add(ComplaintTopic.SUPPLIES);
        if (data.getSaturation() <= 1) topics.add(ComplaintTopic.FOOD);
        return topics;
    }

    public static ComplaintPace pace(ICitizenData data) {
        if (!(data instanceof CitizenDataPersonalityExtended personality)) return ComplaintPace.NORMAL;
        PersonalityArchetype archetype = personality.mc_talking$getPersonality();
        return ComplaintPace.of(archetype == null ? null : archetype.name());
    }

    /**
     * Whether a fix is visibly on its way: a builder has a work order for a residence (their own home
     * when they have one), or for a workplace while they have no job.
     */
    public static boolean progressVisible(ICitizenData data, ComplaintTopic topic) {
        if (topic != ComplaintTopic.HOUSING && topic != ComplaintTopic.WORK) return false;
        IColony colony = data.getColony();
        IBuilding home = data.getHomeBuilding();
        for (IServerWorkOrder order : colony.getWorkManager().getWorkOrders().values()) {
            if (order.getWorkOrderType() == WorkOrderType.REMOVE) continue;
            IBuilding building = colony.getServerBuildingManager().getBuilding(order.getLocation());
            if (building == null) continue;
            boolean residence = RESIDENCE.equals(building.getBuildingType().getRegistryName().getPath());
            if (topic == ComplaintTopic.HOUSING && residence && (home == null || home == building)) return true;
            if (topic == ComplaintTopic.WORK && !residence) return true;
        }
        return false;
    }

    /** The notes for every active problem and the residues, towards {@code player}. */
    public static @Nullable ComplaintContext context(ICitizenData data, @Nullable UUID player) {
        ComplaintHistory history = history(data);
        if (history == null || player == null) return null;
        int day = data.getColony().getDay();
        ComplaintPace pace = pace(data);
        Map<ComplaintTopic, ComplaintHistory.Note> notes = new EnumMap<>(ComplaintTopic.class);
        for (ComplaintTopic topic : activeTopics(data)) {
            notes.put(topic, history.note(topic, player, day, pace, progressVisible(data, topic)));
        }
        return new ComplaintContext(notes, history.residues(player));
    }

    public static @Nullable ComplaintHistory.Note note(ICitizenData data, ComplaintTopic topic, UUID player) {
        ComplaintHistory history = history(data);
        if (history == null) return null;
        return history.note(topic, player, data.getColony().getDay(), pace(data), progressVisible(data, topic));
    }

    /** Whether the citizen gave up asking {@code player} about {@code topic}: it no longer walks up about it. */
    public static boolean hasGivenUp(ICitizenData data, @Nullable ComplaintTopic topic, UUID player) {
        if (topic == null) return false;
        ComplaintHistory.Note note = note(data, topic, player);
        return note != null && note.stage() == ComplaintStage.RESIGNED;
    }

    /** The citizen raised {@code topic} with {@code player}; ignored when it is not a current problem. */
    public static boolean recordRaised(ICitizenData data, ComplaintTopic topic, UUID player) {
        ComplaintHistory history = history(data);
        if (history == null || !activeTopics(data).contains(topic)) return false;
        boolean counted = history.recordRaised(topic, player, data.getColony().getDay());
        if (counted) McTalking.LOGGER.info("[Complaints] {} raised {} with {}", data.getName(), topic.id(), player);
        return counted;
    }

    /** The player said something to the citizen in a conversation. */
    public static void recordAnswered(AbstractEntityCitizen citizen, UUID player) {
        ICitizenData data = citizen.getCitizenData();
        ComplaintHistory history = history(data);
        if (history != null) history.recordAnswered(player, data.getColony().getDay());
    }

    /** Forgets fixed problems, once a minute. */
    public static void tick(MinecraftServer server) {
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            for (ICitizenData data : colony.getCitizenManager().getCitizens()) {
                ComplaintHistory history = history(data);
                if (history == null || history.isEmpty()) continue;
                history.resolve(activeTopics(data), colony.getDay(), pace(data));
            }
        }
    }
}
