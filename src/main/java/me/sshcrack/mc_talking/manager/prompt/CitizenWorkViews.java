package me.sshcrack.mc_talking.manager.prompt;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.ITimeBasedHappinessModifier;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.view.AIWorkerState;
import me.sshcrack.mc_talking.api.prompt.view.BuilderActivityStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenAIState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityCategory;
import me.sshcrack.mc_talking.api.prompt.view.CitizenRequestAvailabilityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenSubState;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.api.prompt.view.ObservationState;
import me.sshcrack.mc_talking.api.prompt.view.ObservedValue;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.duck.CitizenRecentActionsProvider;
import me.sshcrack.mc_talking.internal.compat.MineColoniesCompatibilityMapper;
import me.sshcrack.mc_talking.manager.BuilderActivityClassifier;
import me.sshcrack.mc_talking.mixin.CitizenDataAccessor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds the work part of the prompt context (moved from CitizenPromptViewFactory). */
public final class CitizenWorkViews {
    private CitizenWorkViews() {
    }

    public record CategorizedRequests(
            @Nullable List<String> fulfillable,
            @Nullable List<String> blocked
    ) {
        static final CategorizedRequests EMPTY = new CategorizedRequests(null, null);
    }

    public record RequestSnapshot(
            @NotNull ObservedValue<CitizenRequestAvailabilityView> observation,
            @NotNull CategorizedRequests categorized
    ) {
    }

    @Nullable
    public static String extractJobName(ICitizenData data) {
        if (data.getJob() == null) {
            return null;
        }
        return Component.translatable(data.getJob().getJobRegistryEntry().getTranslationKey()).getString();
    }

    public record ActivityParts(
            CitizenActivityCategory category,
            @Nullable CitizenAIState citizenState,
            @Nullable CitizenSubState subState
    ) {
        static ActivityParts other() {
            return new ActivityParts(CitizenActivityCategory.OTHER, CitizenAIState.UNKNOWN, null);
        }
    }

    public static ActivityParts extractActivityParts(ICitizenData data) {
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
                    new CitizenSubState(CitizenAIState.EATING, MineColoniesCompatibilityMapper.eatingState(es), CitizenNeedsViews.deriveRestaurantContext(data)));
        }
        if (state instanceof com.minecolonies.core.entity.ai.minimal.EntityAISleep.SleepState ss) {
            return new ActivityParts(CitizenActivityCategory.SLEEPING, CitizenAIState.SLEEP,
                    new CitizenSubState(CitizenAIState.SLEEP, MineColoniesCompatibilityMapper.sleepState(ss), CitizenNeedsViews.deriveHomeName(data)));
        }
        if (state instanceof com.minecolonies.core.entity.ai.minimal.EntityAISickTask.DiseaseState ds) {
            return new ActivityParts(CitizenActivityCategory.SICK, CitizenAIState.SICK,
                    new CitizenSubState(CitizenAIState.SICK, MineColoniesCompatibilityMapper.diseaseState(ds), CitizenNeedsViews.deriveDiseaseName(data)));
        }
        if (state instanceof com.minecolonies.core.entity.ai.minimal.EntityAIMournCitizen.MourningState ms) {
            return new ActivityParts(CitizenActivityCategory.MOURNING, CitizenAIState.MOURN,
                    new CitizenSubState(CitizenAIState.MOURN, MineColoniesCompatibilityMapper.mourningState(ms), CitizenNeedsViews.deriveDeceasedName(data)));
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

    @Nullable
    public static AIWorkerState extractWorkState(ICitizenData data) {
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

    public static BuilderActivityStatus extractBuilderActivity(
            ICitizenData data,
            @Nullable AIWorkerState workState,
            ObservedValue<CitizenRequestAvailabilityView> requests
    ) {
        boolean builder = data.getJob() != null
                && data.getJob().getJobRegistryEntry() == ModJobs.builder.get();
        return BuilderActivityClassifier.classify(
                builder, data.isAsleep(), data.getJobStatus(), workState, requests);
    }

    public static List<String> extractBlockingMessages(ICitizenData data) {
        return ((CitizenDataAccessor) data)
                .getCitizenChatOptions()
                .values()
                .stream()
                .filter(e -> e.getPriority().getPriority() >= ChatPriority.IMPORTANT.getPriority())
                .map(e -> e.getInquiry().getString())
                .toList();
    }

    public static RequestSnapshot extractRequestSnapshot(
            ICitizenData data,
            @Nullable IBuilding workBuilding,
            long gameTime
    ) {
        if (workBuilding == null) {
            var value = new CitizenRequestAvailabilityView(List.of(), List.of());
            return new RequestSnapshot(ObservedValue.current(value, gameTime), CategorizedRequests.EMPTY);
        }
        Collection<IRequest<?>> openRequests = workBuilding.getOpenRequests(data.getId());
        if (openRequests == null) {
            return new RequestSnapshot(
                    ObservedValue.unavailable(ObservationState.UNAVAILABLE, gameTime),
                    CategorizedRequests.EMPTY
            );
        }

        Set<RequestState> terminalStates = EnumSet.of(
                RequestState.CANCELLED, RequestState.FAILED, RequestState.COMPLETED,
                RequestState.OVERRULED, RequestState.RECEIVED
        );
        List<String> assigned = new ArrayList<>();
        List<String> waiting = new ArrayList<>();

        for (IRequest<?> request : openRequests) {
            if (terminalStates.contains(request.getState())) continue;
            String display;
            var requestable = request.getRequest();
            if (requestable instanceof Stack stackReq) {
                display = stackReq.getCount() + "x " + stackReq.getStack().getDisplayName().getString();
            } else {
                display = request.getShortDisplayString().getString();
            }
            switch (request.getState()) {
                case ASSIGNED, IN_PROGRESS, RESOLVED, FOLLOWUP_IN_PROGRESS, FINALIZING -> assigned.add(display);
                default -> waiting.add(display);
            }
        }
        var value = new CitizenRequestAvailabilityView(assigned, waiting);
        var categorized = new CategorizedRequests(
                assigned.isEmpty() ? null : assigned,
                waiting.isEmpty() ? null : waiting
        );
        return new RequestSnapshot(ObservedValue.current(value, gameTime), categorized);
    }

    @Nullable
    public static List<String> extractRecentActions(ICitizenData data) {
        if (!(data instanceof CitizenRecentActionsProvider provider)) return null;
        var actions = provider.mc_talking$getRecentActions();
        return actions.isEmpty() ? null : actions;
    }

    @Nullable
    public static List<String> extractActiveQuests(ICitizenData data) {
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

    public static List<HappinessModifierView> extractHappinessModifiers(ICitizenData data) {
        var handler = data.getCitizenHappinessHandler();
        return handler.getModifiers().stream()
                .map(modifierId -> {
                    IHappinessModifier modifier = handler.getModifier(modifierId);
                    if (modifier == null) {
                        return null;
                    }
                    int activeDays = modifier instanceof ITimeBasedHappinessModifier timed ? timed.getDays() : 0;
                    return new HappinessModifierView(MineColoniesCompatibilityMapper.happinessModifier(modifierId),
                            modifier.getFactor(data), activeDays);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public static List<SkillLevelView> extractSkills(ICitizenData data) {
        if (data.getCitizenSkillHandler() == null) {
            return List.of();
        }
        return data.getCitizenSkillHandler().getSkills().entrySet().stream()
                .map(e -> new SkillLevelView(MineColoniesCompatibilityMapper.skill(e.getKey()), e.getValue().getLevel()))
                .toList();
    }
}
