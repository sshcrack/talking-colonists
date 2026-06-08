package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IAnimalData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.connections.ColonyConnection;
import com.minecolonies.api.colony.connections.DiplomacyStatus;
import com.minecolonies.api.colony.connections.IColonyConnectionManager;
import com.minecolonies.api.colony.managers.interfaces.IAnimalManager;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.util.Tuple;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusType;
import me.sshcrack.mc_talking.util.ColonyStatsHelper;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.api.prompt.view.PlayerRelationView;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.config.PersonalityArchetype;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.duck.CitizenDataPersonalityExtended;
import me.sshcrack.mc_talking.mixin.CitizenDataAccessor;
import me.sshcrack.mc_talking.util.MiscUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;


import static com.minecolonies.api.util.constant.HappinessConstants.DAMAGE;
import static com.minecolonies.api.util.constant.HappinessConstants.DEATH;
import static com.minecolonies.api.util.constant.HappinessConstants.FOOD;
import static com.minecolonies.api.util.constant.HappinessConstants.HEALTH;
import static com.minecolonies.api.util.constant.HappinessConstants.HOMELESSNESS;
import static com.minecolonies.api.util.constant.HappinessConstants.IDLEATJOB;
import static com.minecolonies.api.util.constant.HappinessConstants.MYSTICAL_SITE;
import static com.minecolonies.api.util.constant.HappinessConstants.RAIDWITHOUTDEATH;
import static com.minecolonies.api.util.constant.HappinessConstants.SCHOOL;
import static com.minecolonies.api.util.constant.HappinessConstants.SECURITY;
import static com.minecolonies.api.util.constant.HappinessConstants.SLEPTTONIGHT;
import static com.minecolonies.api.util.constant.HappinessConstants.SOCIAL;
import static com.minecolonies.api.util.constant.HappinessConstants.UNEMPLOYMENT;

import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;

/**
 * Builds stable API prompt views from MineColonies runtime data.
 */
public final class CitizenPromptViewFactory {

    private CitizenPromptViewFactory() {
    }

    public static CitizenPromptView create(ICitizenData data, @NotNull Map<UUID, String> interestedParties, @Nullable ServerPlayer speakingTo) {
        String jobName = extractJobName(data);
        List<String> parents = extractParents(data);
        Double healthPercent = extractHealthPercent(data);
        double happiness = data.getCitizenHappinessHandler().getHappiness(data.getColony(), data);
        List<HappinessModifierView> modifiers = extractHappinessModifiers(data);
        boolean hasSchool = data.getColony().getServerBuildingManager().hasBuilding(
                ModBuildings.school.get().getRegistryName(),
                1,
                true
        );
        List<SkillLevelView> skills = extractSkills(data);
        List<String> blockingMessages = extractBlockingMessages(data);
        PlayerRelationView relation = extractPlayerRelation(data, speakingTo);
        List<String> childrenNames = extractChildrenNames(data);
        List<String> siblingNames = extractSiblingNames(data);
        String colonyName = data.getColony().getName();
        IBuilding homeBuilding = data.getHomeBuilding();
        String homeBuildingDisplayName = homeBuilding != null ? homeBuilding.getBuildingDisplayName() : null;
        int homeBuildingLevel = homeBuilding != null ? homeBuilding.getBuildingLevel() : 0;
        IBuilding workBuilding = data.getWorkBuilding();
        String workBuildingDisplayName = workBuilding != null ? workBuilding.getBuildingDisplayName() : null;
        int workBuildingLevel = workBuilding != null ? workBuilding.getBuildingLevel() : 0;
        var personalityExt = (CitizenDataPersonalityExtended) data;
        personalityExt.mc_talking$assignPersonality();
        PersonalityArchetype personality = personalityExt.mc_talking$getPersonality();
        String customPersonalityText = personalityExt.mc_talking$getCustomPersonality();
        String playerState = extractPlayerState(speakingTo);
        var envInfo = extractEnvironmentInfo(data);
        String colonyMilestone = ColonyStatsHelper.getColonyMilestoneText(data);
        List<String> activeItemRequests = extractActiveItemRequests(data, workBuilding);
        List<String> activeQuests = extractActiveQuests(data);
        boolean isGuard = data.getJob() != null && data.getJob().isGuard();
        List<String> colonyConnections = extractColonyConnections(data);
        List<String> recentEvents = extractRecentEvents(data);
        String citizenAiState = extractCitizenAiState(data);
        String workAiState = extractWorkAiState(data);
        String nameTagDescription = extractNameTagDescription(data);
        String animalSummary = extractAnimalInfo(data);

        return new CitizenPromptView(
                data.getName(),
                data.isChild(),
                data.isFemale(),
                jobName,
                isGuard,
                data.getCitizenDiseaseHandler().isSick(),
                data.getHomeBuilding() == null && !isGuard,
                parents,
                data.getPartner() != null,
                childrenNames,
                siblingNames,
                data.getSaturation(),
                healthPercent,
                createStatusView(data.getStatus(), data),
                happiness,
                modifiers,
                hasSchool,
                skills,
                blockingMessages,
                relation,
                getLanguageNameFromCode(McTalkingConfig.INSTANCE.instance().language),
                ((CitizenDataMemoryExtended) data).mc_talking$getMemory(),
                interestedParties,
                colonyName,
                homeBuildingDisplayName,
                homeBuildingLevel,
                workBuildingDisplayName,
                workBuildingLevel,
                data.getColony().getID(),
                envInfo.peaceful(),
                personality,
                customPersonalityText,
                playerState,
                envInfo.description(),
                activeItemRequests,
                activeQuests,
                recentEvents,
                colonyConnections,
                colonyMilestone,
                citizenAiState,
                workAiState,
                nameTagDescription,
                animalSummary
        );
    }

    // ── Extracted helpers ────────────────────────────────────────────────

    @Nullable
    private static String extractJobName(ICitizenData data) {
        if (data.getJob() == null) {
            return null;
        }
        return Component.translatable(data.getJob().getJobRegistryEntry().getTranslationKey()).getString();
    }

    @Nullable
    private static String extractCitizenAiState(ICitizenData data) {
        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty()) return null;
        var entity = entityOpt.get();
        if (!(entity instanceof com.minecolonies.core.entity.citizen.EntityCitizen citizen)) return null;
        var ai = citizen.getCitizenAI();
        if (ai == null) return null;
        var state = ai.getState();
        return state != null ? state.toString() : null;
    }

    @Nullable
    private static String extractWorkAiState(ICitizenData data) {
        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty()) return null;
        var entity = entityOpt.get();
        var jobHandler = entity.getCitizenJobHandler();
        if (jobHandler == null) return null;
        var workAi = jobHandler.getWorkAI();
        if (workAi == null) return null;
        var stateAi = workAi.getStateAI();
        if (stateAi == null) return null;
        var state = stateAi.getState();
        return state != null ? state.toString() : null;
    }

    @Nullable
    private static String extractNameTagDescription(ICitizenData data) {
        if (data.getJob() == null) return null;
        return data.getJob().getNameTagDescription();
    }

    private static List<String> extractParents(ICitizenData data) {
        List<String> parents = new ArrayList<>();
        Tuple<String, String> parentTuple = data.getParents();
        if (parentTuple != null) {
            if (parentTuple.getA() != null && !parentTuple.getA().isEmpty()) {
                parents.add(parentTuple.getA());
            }
            if (parentTuple.getB() != null && !parentTuple.getB().isEmpty()) {
                parents.add(parentTuple.getB());
            }
        }
        return parents;
    }

    @Nullable
    private static Double extractHealthPercent(ICitizenData data) {
        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty()) {
            return null;
        }
        var entity = entityOpt.get();
        return (entity.getHealth() / Math.max(1.0, entity.getMaxHealth())) * 100.0;
    }

    private static List<HappinessModifierView> extractHappinessModifiers(ICitizenData data) {
        var handler = data.getCitizenHappinessHandler();
        return handler.getModifiers().stream()
                .map(modifierId -> {
                    IHappinessModifier modifier = handler.getModifier(modifierId);
                    if (modifier == null) {
                        return null;
                    }
                    return new HappinessModifierView(resolveHappinessModifierType(modifierId), modifier.getFactor(data));
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<SkillLevelView> extractSkills(ICitizenData data) {
        if (data.getCitizenSkillHandler() == null) {
            return List.of();
        }
        return data.getCitizenSkillHandler().getSkills().entrySet().stream()
                .map(e -> new SkillLevelView(e.getKey().name(), e.getValue().getLevel()))
                .toList();
    }

    private static List<String> extractBlockingMessages(ICitizenData data) {
        return ((CitizenDataAccessor) data)
                .getCitizenChatOptions()
                .values()
                .stream()
                .filter(e -> e.getPriority().getPriority() >= ChatPriority.IMPORTANT.getPriority())
                .map(e -> e.getInquiry().getString())
                .toList();
    }

    @Nullable
    private static PlayerRelationView extractPlayerRelation(ICitizenData data, @Nullable ServerPlayer speakingTo) {
        if (speakingTo == null) {
            return null;
        }
        String speakingName = speakingTo.getName().getString();
        var perms = data.getColony().getPermissions().getPlayers().get(speakingTo.getUUID());
        if (perms == null) {
            return null;
        }
        var rank = perms.getRank();
        String rankName = getRankName(rank);
        return new PlayerRelationView(speakingName, rankName, rank.isHostile(), rank.isColonyManager() || rank.isInitial());
    }

    private static List<String> extractChildrenNames(ICitizenData data) {
        List<String> names = new ArrayList<>();
        if (data.getChildren() != null) {
            for (int childId : data.getChildren()) {
                var child = data.getColony().getCitizen(childId);
                names.add(child.getName());
            }
        }
        return names;
    }

    private static List<String> extractSiblingNames(ICitizenData data) {
        List<String> names = new ArrayList<>();
        if (data.getSiblings() != null) {
            for (int siblingId : data.getSiblings()) {
                var sibling = data.getColony().getCitizen(siblingId);
                names.add(sibling.getName());
            }
        }
        return names;
    }

    @Nullable
    private static String extractPlayerState(@Nullable ServerPlayer speakingTo) {
        if (speakingTo == null) {
            return null;
        }
        float health = speakingTo.getHealth();
        float maxHealth = speakingTo.getMaxHealth();
        int armorValue = speakingTo.getArmorValue();
        StringBuilder ps = new StringBuilder();
        float healthPct = health / Math.max(1.0f, maxHealth);
        if (healthPct > 0.75f) ps.append("healthy");
        else if (healthPct > 0.50f) ps.append("lightly wounded");
        else if (healthPct > 0.25f) ps.append("wounded");
        else ps.append("severely injured");
        ps.append(" (").append(Math.round(health)).append("/").append(Math.round(maxHealth)).append(" HP)");
        if (armorValue > 0) {
            int wornPieces = 0;
            float bestToughness = 0;
            String bestArmorName = "";
            EquipmentSlot[] toCheck = {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
            };
            for (EquipmentSlot slot : toCheck) {
                ItemStack itemStack = speakingTo.getItemBySlot(slot);
                if (itemStack.isEmpty())
                    continue;
                if (itemStack.getItem() instanceof ArmorItem armor) {
                    float toughness = armor.getToughness();
                    if (toughness > bestToughness) {
                        bestToughness = toughness;
                        bestArmorName = itemStack.getDisplayName().getString();
                    }
                    wornPieces++;
                }
            }
            ps.append(", wearing ").append(wornPieces).append(" armor pieces");
            if (!bestArmorName.isEmpty())
                ps.append(String.format(" (best: %s)", bestArmorName));
        } else {
            ps.append(", no armor");
        }
        return ps.toString();
    }

    private static EnvironmentInfo extractEnvironmentInfo(ICitizenData data) {
        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty()) {
            return new EnvironmentInfo(null, false);
        }
        var entity = entityOpt.get();
        Level level = entity.level();
        long dayTime = level.getDayTime() % 24000L;
        String description = "It is " + MiscUtil.describeTime(dayTime) + " and " + describeWeather(level) + ".";
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        return new EnvironmentInfo(description, peaceful);
    }

    @Nullable
    private static List<String> extractActiveItemRequests(ICitizenData data, @Nullable IBuilding workBuilding) {
        if (workBuilding == null) {
            return null;
        }
        Collection<IRequest<?>> openRequests = workBuilding.getOpenRequests(data.getId());
        if (openRequests == null || openRequests.isEmpty()) {
            return null;
        }
        List<String> requests = new ArrayList<>();
        for (IRequest<?> request : openRequests) {
            var requestable = request.getRequest();
            if (requestable instanceof Stack stackReq) {
                ItemStack itemStack = stackReq.getStack();
                int count = stackReq.getCount();
                requests.add(count + "x " + itemStack.getDisplayName().getString());
            } else {
                requests.add(request.getShortDisplayString().getString());
            }
        }
        return requests;
    }

    @Nullable
    private static List<String> extractActiveQuests(ICitizenData data) {
        if (!data.hasQuestAssignment()) {
            return null;
        }
        var colony = data.getColony();
        var questManager = colony.getQuestManager();
        var inProgress = questManager.getInProgressQuests();
        if (inProgress == null || inProgress.isEmpty()) {
            return null;
        }
        List<String> quests = new ArrayList<>();
        int citizenId = data.getId();
        for (var quest : inProgress) {
            var participants = quest.getParticipants();
            if (participants != null && participants.contains(citizenId)) {
                String questName = quest.getId().getPath()
                        .replace("_", " ")
                        .replace("/", " - ");
                int rawObjective = quest.getObjectiveIndex();
                if (rawObjective >= 0) {
                    int objective = rawObjective + 1;
                    questName += " (objective " + objective + ")";
                }
                quests.add(questName);
            }
        }
        return quests.isEmpty() ? null : quests;
    }

    @Nullable
    private static List<String> extractColonyConnections(ICitizenData data) {
        if (!McTalkingConfig.INSTANCE.instance().enableColonyDiplomacy) {
            return null;
        }
        try {
            IColonyConnectionManager connManager = data.getColony().getConnectionManager();
            if (connManager == null) {
                return null;
            }
            List<String> connections = new ArrayList<>();
            TreeMap<Integer, ColonyConnection> direct = connManager.getDirectlyConnectedColonies();
            if (direct != null) {
                for (Map.Entry<Integer, ColonyConnection> entry : direct.entrySet()) {
                    try {
                        int targetId = entry.getKey();
                        ColonyConnection conn = entry.getValue();
                        String connName = conn.name != null ? conn.name : "Colony #" + targetId;
                        DiplomacyStatus status = connManager.getColonyDiplomacyStatus(targetId);
                        connections.add(connName + " (" + (status != null ? status.name() : "unknown") + ")");
                    } catch (Exception e) {
                        McTalking.LOGGER.warn("Failed to process direct colony connection {}", entry.getKey(), e);
                    }
                }
            }
            TreeMap<Integer, ColonyConnection> indirect = connManager.getIndirectlyConnectedColonies();
            if (indirect != null) {
                for (Map.Entry<Integer, ColonyConnection> entry : indirect.entrySet()) {
                    try {
                        int targetId = entry.getKey();
                        ColonyConnection conn = entry.getValue();
                        String connName = conn.name != null ? conn.name : "Colony #" + targetId;
                        DiplomacyStatus status = connManager.getColonyDiplomacyStatus(targetId);
                        connections.add(connName + " (" + (status != null ? status.name() : "unknown") + ")");
                    } catch (Exception e) {
                        McTalking.LOGGER.warn("Failed to process indirect colony connection {}", entry.getKey(), e);
                    }
                }
            }
            return connections.isEmpty() ? null : connections;
        } catch (Exception e) {
            McTalking.LOGGER.warn("Failed to extract colony connections", e);
            return null;
        }
    }

    private static List<String> extractRecentEvents(ICitizenData data) {
        int colonyId = data.getColony().getID();
        int eventWindow = McTalkingConfig.INSTANCE.instance().colonyEventWindowSeconds;
        if (eventWindow <= 0) {
            return List.of();
        }
        return ColonyEventBuffer.getRecentEvents(colonyId, eventWindow).stream()
                .map(ColonyEventBuffer.ColonyEvent::description)
                .toList();
    }

    private record EnvironmentInfo(@Nullable String description, boolean peaceful) {}

    @NotNull
    private static String getRankName(Rank rank) {
        if (rank.isHostile()) {
            return "enemy";
        }

        if (rank.isColonyManager())
            return "manager";

        if (rank.isInitial())
            return "leader";

        return "visitor";
    }

    public static CitizenStatusView createStatusView(VisibleCitizenStatus status, ICitizenData data) {
        if (status == null) {
            return null;
        }

        List<String> contextValues = List.of();
        if (status == VisibleCitizenStatus.MOURNING) {
            contextValues = new ArrayList<>(data.getCitizenMournHandler().getDeceasedCitizens());
        }

        return new CitizenStatusView(resolveStatusId(status), status.getTranslationKey(), contextValues);
    }

    private static CitizenStatusType resolveStatusId(VisibleCitizenStatus status) {
        if (status == VisibleCitizenStatus.WORKING) {
            return CitizenStatusType.WORKING;
        }
        if (status == VisibleCitizenStatus.SLEEP) {
            return CitizenStatusType.SLEEP;
        }
        if (status == VisibleCitizenStatus.HOUSE) {
            return CitizenStatusType.HOUSE;
        }
        if (status == VisibleCitizenStatus.RAIDED) {
            return CitizenStatusType.RAIDED;
        }
        if (status == VisibleCitizenStatus.MOURNING) {
            return CitizenStatusType.MOURNING;
        }
        if (status == VisibleCitizenStatus.BAD_WEATHER) {
            return CitizenStatusType.BAD_WEATHER;
        }
        if (status == VisibleCitizenStatus.SICK) {
            return CitizenStatusType.SICK;
        }
        if (status == VisibleCitizenStatus.EAT) {
            return CitizenStatusType.EAT;
        }

        return CitizenStatusType.UNKNOWN;
    }

    private static String getLanguageNameFromCode(String localeCode) {
        try {
            String[] parts = localeCode.split("-");
            String languageCode = parts[0];
            Locale locale = Locale.forLanguageTag(languageCode);
            return locale.getDisplayLanguage(Locale.ENGLISH);
        } catch (Exception e) {
            return localeCode;
        }
    }

    private static HappinessModifierType resolveHappinessModifierType(String modifierId) {
        if (HOMELESSNESS.equals(modifierId)) {
            return HappinessModifierType.HOMELESSNESS;
        }
        if (UNEMPLOYMENT.equals(modifierId)) {
            return HappinessModifierType.UNEMPLOYMENT;
        }
        if (HEALTH.equals(modifierId)) {
            return HappinessModifierType.HEALTH;
        }
        if (IDLEATJOB.equals(modifierId)) {
            return HappinessModifierType.IDLEATJOB;
        }
        if (SCHOOL.equals(modifierId)) {
            return HappinessModifierType.SCHOOL;
        }
        if (MYSTICAL_SITE.equals(modifierId)) {
            return HappinessModifierType.MYSTICAL_SITE;
        }
        if (SECURITY.equals(modifierId)) {
            return HappinessModifierType.SECURITY;
        }
        if (SOCIAL.equals(modifierId)) {
            return HappinessModifierType.SOCIAL;
        }
        if (DAMAGE.equals(modifierId)) {
            return HappinessModifierType.DAMAGE;
        }
        if (DEATH.equals(modifierId)) {
            return HappinessModifierType.DEATH;
        }
        if (RAIDWITHOUTDEATH.equals(modifierId)) {
            return HappinessModifierType.RAIDWITHOUTDEATH;
        }
        if (FOOD.equals(modifierId)) {
            return HappinessModifierType.FOOD;
        }
        if (SLEPTTONIGHT.equals(modifierId)) {
            return HappinessModifierType.SLEPTTONIGHT;
        }

        return HappinessModifierType.UNKNOWN;
    }

    private static String describeWeather(Level level) {
        if (level.isThundering()) return "thundering";
        if (level.isRaining()) return "rainy";
        return "clear";
    }

    @Nullable
    private static String extractAnimalInfo(ICitizenData data) {
        if (!McTalkingConfig.INSTANCE.instance().enableAnimalAwareness) return null;
        var colony = data.getColony();
        if (colony == null) return null;
        IAnimalManager mgr = colony.getAnimalManager();
        if (mgr == null) return null;
        int count = mgr.getCurrentAnimalCount();
        if (count == 0) return null;
        return count + " managed animal(s)";
    }
}
