package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.api.colony.connections.ColonyConnection;
import com.minecolonies.api.colony.connections.DiplomacyStatus;
import com.minecolonies.api.colony.connections.IColonyConnectionManager;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.BuildingView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityCategory;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenAIState;
import me.sshcrack.mc_talking.api.prompt.view.AIWorkerState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenSubState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.CitizenFamilyView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenIdentityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWellbeingView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWorkView;
import me.sshcrack.mc_talking.api.prompt.view.ColonyPromptView;
import me.sshcrack.mc_talking.api.prompt.view.ConversationPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPersonalityView;
import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import me.sshcrack.mc_talking.conversations.memory.MemorySnapshotFactory;
import me.sshcrack.mc_talking.api.prompt.view.ColonyFoodSituation;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import me.sshcrack.mc_talking.util.ColonyStatsHelper;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.api.prompt.view.PlayerRelationView;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.config.PersonalityArchetype;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.duck.CitizenDataPersonalityExtended;
import me.sshcrack.mc_talking.duck.CitizenRecentActionsProvider;
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



import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.util.MumblingTopicHelper;
import net.minecraft.core.BlockPos;

/**
 * Builds stable API prompt views from MineColonies runtime data.
 */
public final class CitizenPromptViewFactory {

    private CitizenPromptViewFactory() {
    }

    private record CategorizedRequests(
            @Nullable List<String> fulfillable,
            @Nullable List<String> blocked
    ) {
        static final CategorizedRequests EMPTY = new CategorizedRequests(null, null);
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
        String homeBuildingDisplayName = getReadableBuildingName(homeBuilding);
        int homeBuildingLevel = homeBuilding != null ? homeBuilding.getBuildingLevel() : 0;
        IBuilding workBuilding = data.getWorkBuilding();
        String workBuildingDisplayName = getReadableBuildingName(workBuilding);
        int workBuildingLevel = workBuilding != null ? workBuilding.getBuildingLevel() : 0;
        var personalityExt = (CitizenDataPersonalityExtended) data;
        personalityExt.mc_talking$assignPersonality();
        PersonalityArchetype personality = personalityExt.mc_talking$getPersonality();
        CitizenPersonalityView personalityView = personality == null ? null : new CitizenPersonalityView(
                personality.name().toLowerCase(Locale.ROOT), personality.getPromptLines(), false);
        String customPersonalityText = personalityExt.mc_talking$getCustomPersonality();
        var memory = ((CitizenDataMemoryExtended) data).mc_talking$getMemory();
        CitizenMemorySnapshot memorySnapshot = memory == null
                ? null
                : MemorySnapshotFactory.create(memory);
        String playerState = extractPlayerState(speakingTo);
        var envInfo = extractEnvironmentInfo(data);
        String colonyMilestone = ColonyStatsHelper.getColonyMilestoneText(data);
        CategorizedRequests categorizedRequests = extractCategorizedItemRequests(data, workBuilding);
        List<String> activeQuests = extractActiveQuests(data);
        boolean isGuard = data.getJob() != null && data.getJob().isGuard();
        List<String> colonyConnections = extractColonyConnections(data);
        IColony colony = data.getColony();
        long lastRaidEndTime = ColonyEventBuffer.getLastRaidEndTime(colony);
        Long lastRaidEndTimeTicks = lastRaidEndTime != Long.MAX_VALUE ? lastRaidEndTime : null;
        int lastRaidLostCitizens = ColonyEventBuffer.getLostCitizens(colony);
        long currentGameTimeTicks = colony.getWorld() != null ? colony.getWorld().getGameTime() : 0;
        List<String> recentEvents = extractRecentEvents(data);
        ActivityParts activityParts = extractActivityParts(data);
        AIWorkerState workState = extractWorkState(data);
        String nameTagDescription = extractNameTagDescription(data);
        String colonyFoundingPlayer = data.getColony().getPermissions().getOwnerName();
        int colonyAgeDays = data.getColony().getDay();
        ColonyFoodSituation colonyFoodSituation = extractFoodSituation(data, activityParts.category());
        List<String> recentActions = extractRecentActions(data);

        CitizenStatusView statusView = createStatusView(data.getStatus(), data);
        CitizenActivityView activity = new CitizenActivityView(
                activityParts.category(),
                statusView,
                activityParts.citizenState(),
                workState,
                activityParts.subState(),
                AIStateDescriber.describe(
                        activityParts.category(),
                        workState,
                        activityParts.subState(),
                        nameTagDescription,
                        workBuildingDisplayName
                ),
                nameTagDescription,
                recentActions == null ? List.of() : recentActions
        );

        return new CitizenPromptSnapshot(
                new CitizenIdentityView(
                        data.getName(), data.isChild(), data.isFemale(), isGuard,
                        personalityView, customPersonalityText
                ),
                new CitizenFamilyView(parents, data.getPartner() != null, childrenNames, siblingNames),
                new CitizenWellbeingView(
                        data.getCitizenDiseaseHandler().isSick(),
                        data.getHomeBuilding() == null && !isGuard,
                        data.getSaturation(),
                        healthPercent,
                        happiness,
                        modifiers,
                        hasSchool,
                        blockingMessages,
                        colonyFoodSituation
                ),
                new CitizenWorkView(
                        jobName,
                        homeBuildingDisplayName == null ? null : new BuildingView(homeBuildingDisplayName, homeBuildingLevel),
                        workBuildingDisplayName == null ? null : new BuildingView(workBuildingDisplayName, workBuildingLevel),
                        skills,
                        categorizedRequests.fulfillable() == null ? List.of() : categorizedRequests.fulfillable(),
                        categorizedRequests.blocked() == null ? List.of() : categorizedRequests.blocked(),
                        activeQuests == null ? List.of() : activeQuests
                ),
                new ColonyPromptView(
                        data.getColony().getID(),
                        colonyName,
                        envInfo.peaceful(),
                        colonyFoundingPlayer,
                        colonyAgeDays,
                        lastRaidEndTimeTicks,
                        lastRaidLostCitizens,
                        currentGameTimeTicks,
                        recentEvents,
                        colonyConnections == null ? List.of() : colonyConnections,
                        colonyMilestone,
                        envInfo.description()
                ),
                new ConversationPromptView(
                        getLanguageNameFromCode(McTalkingConfig.INSTANCE.instance().language),
                        relation,
                        playerState,
                        interestedParties
                ),
                activity,
                memorySnapshot
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

    private record ActivityParts(
            CitizenActivityCategory category,
            @Nullable CitizenAIState citizenState,
            @Nullable CitizenSubState subState
    ) {
        static ActivityParts other() {
            return new ActivityParts(CitizenActivityCategory.OTHER, CitizenAIState.UNKNOWN, null);
        }
    }

    private static ActivityParts extractActivityParts(ICitizenData data) {
        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty() || !(entityOpt.get() instanceof EntityCitizen citizen)) {
            return new ActivityParts(CitizenActivityCategory.OTHER, null, null);
        }
        var ai = citizen.getCitizenAI();
        if (ai == null || ai.getState() == null) return new ActivityParts(CitizenActivityCategory.OTHER, null, null);
        var state = ai.getState();

        if (state instanceof com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState cs) {
            CitizenAIState stable = MineColoniesCompatibilityMapper.citizenState(cs);
            CitizenActivityCategory category = switch (stable) {
                case IDLE -> CitizenActivityCategory.IDLE;
                case FLEE -> CitizenActivityCategory.DANGER;
                case EATING -> CitizenActivityCategory.EATING;
                case SICK -> CitizenActivityCategory.SICK;
                case SLEEP -> CitizenActivityCategory.SLEEPING;
                case MOURN -> CitizenActivityCategory.MOURNING;
                case WORK, WORKING -> CitizenActivityCategory.WORKING;
                case INACTIVE -> CitizenActivityCategory.INACTIVE;
                case UNKNOWN -> CitizenActivityCategory.OTHER;
            };
            return new ActivityParts(category, stable, null);
        }

        if (state instanceof com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState es) {
            return new ActivityParts(CitizenActivityCategory.EATING, CitizenAIState.EATING,
                    new CitizenSubState(CitizenAIState.EATING, MineColoniesCompatibilityMapper.eatingState(es), deriveRestaurantContext(data)));
        }
        if (state instanceof com.minecolonies.core.entity.ai.minimal.EntityAISleep.SleepState ss) {
            return new ActivityParts(CitizenActivityCategory.SLEEPING, CitizenAIState.SLEEP,
                    new CitizenSubState(CitizenAIState.SLEEP, MineColoniesCompatibilityMapper.sleepState(ss), deriveHomeName(data)));
        }
        if (state instanceof com.minecolonies.core.entity.ai.minimal.EntityAISickTask.DiseaseState ds) {
            return new ActivityParts(CitizenActivityCategory.SICK, CitizenAIState.SICK,
                    new CitizenSubState(CitizenAIState.SICK, MineColoniesCompatibilityMapper.diseaseState(ds), deriveDiseaseName(data)));
        }
        if (state instanceof com.minecolonies.core.entity.ai.minimal.EntityAIMournCitizen.MourningState ms) {
            return new ActivityParts(CitizenActivityCategory.MOURNING, CitizenAIState.MOURN,
                    new CitizenSubState(CitizenAIState.MOURN, MineColoniesCompatibilityMapper.mourningState(ms), deriveDeceasedName(data)));
        }
        if (state instanceof com.minecolonies.core.entity.ai.minimal.EntityAICitizenAvoidEntity.FleeStates fs) {
            return new ActivityParts(CitizenActivityCategory.DANGER, CitizenAIState.FLEE,
                    new CitizenSubState(CitizenAIState.FLEE, MineColoniesCompatibilityMapper.fleeState(fs), null));
        }
        if (state instanceof com.minecolonies.core.entity.ai.minimal.EntityAICitizenWander.WanderState ws) {
            return new ActivityParts(CitizenActivityCategory.IDLE, CitizenAIState.IDLE,
                    new CitizenSubState(CitizenAIState.IDLE, MineColoniesCompatibilityMapper.wanderState(ws), null));
        }

        McTalking.LOGGER.debug("Unmapped citizen AI state: {} ({})", state, state.getClass().getName());
        return ActivityParts.other();
    }

    private static @Nullable AIWorkerState extractWorkState(ICitizenData data) {
        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty()) return null;
        var jobHandler = entityOpt.get().getCitizenJobHandler();
        if (jobHandler == null || jobHandler.getWorkAI() == null || jobHandler.getWorkAI().getStateAI() == null) {
            return null;
        }
        var state = jobHandler.getWorkAI().getStateAI().getState();
        if (state == null) return null;
        if (state instanceof com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState workerState) {
            return MineColoniesCompatibilityMapper.workerState(workerState);
        }
        McTalking.LOGGER.debug("Unmapped work AI state: {} ({})", state, state.getClass().getName());
        return AIWorkerState.UNKNOWN;
    }

    @Nullable
    private static String extractNameTagDescription(ICitizenData data) {
        if (data.getJob() == null) return null;
        return data.getJob().getNameTagDescription();
    }

    @Nullable
    private static ColonyFoodSituation extractFoodSituation(ICitizenData data, CitizenActivityCategory activityCategory) {
        if (data.getSaturation() > 5.0) return null;
        if (activityCategory == CitizenActivityCategory.EATING) return ColonyFoodSituation.ALREADY_EATING;

        var colony = data.getColony();
        var bm = colony.getServerBuildingManager();
        var origin = data.getEntity()
                .map(e -> e.blockPosition())
                .orElseGet(() -> data.getWorkBuilding() != null
                        ? data.getWorkBuilding().getPosition()
                        : BlockPos.ZERO);

        BlockPos best = bm.getBestBuilding(origin, BuildingCook.class);
        if (best == null) return ColonyFoodSituation.NO_RESTAURANT;

        IBuilding rest = bm.getBuilding(best);
        if (rest == null) return ColonyFoodSituation.NO_RESTAURANT;

        boolean staffed = rest.getModule(BuildingModules.COOK_WORK).hasAssignedCitizen();
        return staffed ? ColonyFoodSituation.STAFFED_RESTAURANT : ColonyFoodSituation.UNSTAFFED_RESTAURANT;
    }

    @Nullable
    private static List<String> extractRecentActions(ICitizenData data) {
        if (!(data instanceof CitizenRecentActionsProvider provider)) return null;
        var actions = provider.mc_talking$getRecentActions();
        return actions.isEmpty() ? null : actions;
    }

    @Nullable
    private static String deriveRestaurantContext(ICitizenData data) {
        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty()) return null;
        if (!(entityOpt.get() instanceof EntityCitizen citizen)) return null;
        var colony = data.getColony();
        if (colony == null) return null;
        var bm = colony.getServerBuildingManager();
        if (bm == null) return null;
        var origin = citizen.blockPosition();
        var best = bm.getBestBuilding(origin, BuildingCook.class);
        if (best == null) return null;
        var rest = bm.getBuilding(best);
        if (!(rest instanceof BuildingCook cook)) return null;
        String buildingName = Component.translatable(cook.getBuildingType().getTranslationKey()).getString();
        var cookModule = cook.getModule(BuildingModules.COOK_WORK);
        String cookName = null;
        if (cookModule != null && cookModule.hasAssignedCitizen()) {
            var citizens = cook.getAllAssignedCitizen();
            if (citizens != null && !citizens.isEmpty()) {
                cookName = citizens.iterator().next().getName();
            }
        }
        if (cookName != null) {
            return buildingName + " (" + cookName + ")";
        }
        return buildingName;
    }

    @Nullable
    private static String deriveHomeName(ICitizenData data) {
        var home = data.getHomeBuilding();
        if (home == null) return null;
        String displayName = home.getBuildingDisplayName();
        if (displayName != null && !displayName.isEmpty() && !displayName.contains(".") && !displayName.contains("/")) {
            return displayName;
        }
        return Component.translatable(home.getBuildingType().getTranslationKey()).getString();
    }

    @Nullable
    private static String deriveDiseaseName(ICitizenData data) {
        var handler = data.getCitizenDiseaseHandler();
        if (handler == null) return null;
        var disease = handler.getDisease();
        if (disease == null) return null;
        return disease.name().getString();
    }

    @Nullable
    private static String deriveDeceasedName(ICitizenData data) {
        var mournHandler = data.getCitizenMournHandler();
        if (mournHandler == null) return null;
        var deceased = mournHandler.getDeceasedCitizens();
        if (deceased == null || deceased.isEmpty()) return null;
        return deceased.iterator().next();
    }

    @Nullable
    private static String getReadableBuildingName(@Nullable IBuilding building) {
        if (building == null) return null;
        String displayName = building.getBuildingDisplayName();
        if (displayName != null && !displayName.isEmpty() && !displayName.contains(".") && !displayName.contains("/")) {
            return displayName;
        }
        return Component.translatable(building.getBuildingType().getTranslationKey()).getString();
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
                    return new HappinessModifierView(MineColoniesCompatibilityMapper.happinessModifier(modifierId), modifier.getFactor(data));
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<SkillLevelView> extractSkills(ICitizenData data) {
        if (data.getCitizenSkillHandler() == null) {
            return List.of();
        }
        return data.getCitizenSkillHandler().getSkills().entrySet().stream()
                .map(e -> new SkillLevelView(MineColoniesCompatibilityMapper.skill(e.getKey()), e.getValue().getLevel()))
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

    private static CategorizedRequests extractCategorizedItemRequests(ICitizenData data, @Nullable IBuilding workBuilding) {
        if (workBuilding == null) {
            return CategorizedRequests.EMPTY;
        }
        Collection<IRequest<?>> openRequests = workBuilding.getOpenRequests(data.getId());
        if (openRequests == null || openRequests.isEmpty()) {
            return CategorizedRequests.EMPTY;
        }

        // Terminal states — request is closed, nothing to report
        java.util.Set<RequestState> terminalStates = java.util.EnumSet.of(
                RequestState.CANCELLED,
                RequestState.FAILED,
                RequestState.COMPLETED,
                RequestState.OVERRULED,
                RequestState.RECEIVED
        );

        List<String> fulfillable = new ArrayList<>();
        List<String> blocked = new ArrayList<>();

        for (IRequest<?> request : openRequests) {
            if (terminalStates.contains(request.getState())) {
                continue;
            }

            String display;
            var requestable = request.getRequest();
            if (requestable instanceof Stack stackReq) {
                display = stackReq.getCount() + "x " + stackReq.getStack().getDisplayName().getString();
            } else {
                display = request.getShortDisplayString().getString();
            }

            boolean isFulfillable = request.getState().ordinal() >= RequestState.ASSIGNED.ordinal()
                    || MumblingTopicHelper.warehouseHasStock(data, request);
            if (isFulfillable) {
                fulfillable.add(display);
            } else {
                blocked.add(display);
            }
        }

        return new CategorizedRequests(
                fulfillable.isEmpty() ? null : fulfillable,
                blocked.isEmpty() ? null : blocked
        );
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
        IColony colony = data.getColony();
        int eventWindow = McTalkingConfig.INSTANCE.instance().colonyEventWindowSeconds;
        if (eventWindow <= 0) {
            return List.of();
        }
        return ColonyEventBuffer.getRecentEvents(colony, eventWindow).stream()
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
        if (status == null) return null;

        CitizenStatusType type = MineColoniesCompatibilityMapper.status(status);
        List<String> contextValues = type == CitizenStatusType.MOURNING
                ? new ArrayList<>(data.getCitizenMournHandler().getDeceasedCitizens())
                : List.of();
        String description = switch (type) {
            case WORKING -> "working";
            case SLEEP -> "sleeping";
            case HOUSE -> "at home";
            case RAIDED -> "on alert (raid)";
            case MOURNING -> contextValues.isEmpty()
                    ? "mourning"
                    : "mourning " + String.join(", ", contextValues);
            case BAD_WEATHER -> "sheltering from bad weather";
            case SICK -> "ill and needing care";
            case EAT -> "eating at the restaurant";
            case UNKNOWN -> humanizeTranslationKey(status.getTranslationKey());
        };
        return new CitizenStatusView(type, status.getTranslationKey(), description, contextValues);
    }

    private static String humanizeTranslationKey(String translationKey) {
        int separator = translationKey.lastIndexOf('.');
        String raw = separator >= 0 ? translationKey.substring(separator + 1) : translationKey;
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ');
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

    private static String describeWeather(Level level) {
        if (level.isThundering()) return "thundering";
        if (level.isRaining()) return "rainy";
        return "clear";
    }
}
