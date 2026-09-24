package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.internal.compat.MineColoniesCompatibilityMapper;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.BuildingView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityCategory;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityView;
import me.sshcrack.mc_talking.api.prompt.view.AIWorkerState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusType;
import me.sshcrack.mc_talking.api.prompt.view.CitizenFamilyView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenIdentityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWellbeingView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWorkView;
import me.sshcrack.mc_talking.api.prompt.view.BuilderActivityStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenEquipmentView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenHousingStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenRequestAvailabilityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenVerifiedFactsView;
import me.sshcrack.mc_talking.api.prompt.view.ObservedValue;
import me.sshcrack.mc_talking.api.prompt.view.VisitorPromptView;
import me.sshcrack.mc_talking.api.prompt.view.ConversationPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPersonalityView;
import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import me.sshcrack.mc_talking.conversations.memory.MemorySnapshotFactory;
import me.sshcrack.mc_talking.api.prompt.view.ColonyFoodSituation;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.api.prompt.view.PlayerRelationView;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.config.PersonalityArchetype;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.duck.CitizenDataPersonalityExtended;
import me.sshcrack.mc_talking.manager.prompt.ColonyPromptViewFactory;
import me.sshcrack.mc_talking.manager.prompt.CitizenFamilyViews;
import me.sshcrack.mc_talking.manager.prompt.CitizenNeedsViews;
import me.sshcrack.mc_talking.manager.prompt.CitizenWorkViews;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;

/**
 * Builds stable API prompt views from MineColonies runtime data.
 */
public final class CitizenPromptViewFactory {

    private CitizenPromptViewFactory() {
    }

    public static CitizenPromptView create(ICitizenData data, @NotNull Map<UUID, String> interestedParties, @Nullable ServerPlayer speakingTo) {
        return create(data, interestedParties, speakingTo, speakingTo == null ? null : speakingTo.getUUID());
    }

    public static CitizenPromptView create(
            ICitizenData data,
            @NotNull Map<UUID, String> interestedParties,
            @Nullable ServerPlayer speakingTo,
            @Nullable UUID contextPlayerId
    ) {
        if (data instanceof IVisitorData visitor) {
            return createVisitor(visitor, interestedParties, speakingTo, contextPlayerId);
        }
        String jobName = CitizenWorkViews.extractJobName(data);
        long snapshotGameTime = data.getColony().getWorld() == null ? -1L : data.getColony().getWorld().getGameTime();
        List<String> parents = CitizenFamilyViews.extractParents(data);
        ObservedValue<Double> observedHealth = CitizenNeedsViews.extractObservedHealth(data, snapshotGameTime);
        Double healthPercent = observedHealth.value();
        double happiness = data.getCitizenHappinessHandler().getHappiness(data.getColony(), data);
        List<HappinessModifierView> modifiers = CitizenWorkViews.extractHappinessModifiers(data);
        boolean hasSchool = data.getColony().getServerBuildingManager().hasBuilding(
                ModBuildings.school.get().getRegistryName(),
                1,
                true
        );
        List<SkillLevelView> skills = CitizenWorkViews.extractSkills(data);
        List<String> blockingMessages = CitizenWorkViews.extractBlockingMessages(data);
        PlayerRelationView relation = CitizenFamilyViews.extractPlayerRelation(data, speakingTo);
        List<String> childrenNames = CitizenFamilyViews.extractChildrenNames(data);
        List<String> siblingNames = CitizenFamilyViews.extractSiblingNames(data);
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
        CitizenWorkViews.RequestSnapshot requestSnapshot = CitizenWorkViews.extractRequestSnapshot(data, workBuilding, snapshotGameTime);
        CitizenWorkViews.CategorizedRequests categorizedRequests = requestSnapshot.categorized();
        List<String> activeQuests = CitizenWorkViews.extractActiveQuests(data);
        boolean isGuard = data.getJob() != null && data.getJob().isGuard();
        CitizenWorkViews.ActivityParts activityParts = CitizenWorkViews.extractActivityParts(data);
        AIWorkerState workState = CitizenWorkViews.extractWorkState(data);
        String nameTagDescription = extractNameTagDescription(data);
        ColonyFoodSituation colonyFoodSituation = CitizenNeedsViews.extractFoodSituation(data, activityParts.category());
        List<String> recentActions = CitizenWorkViews.extractRecentActions(data);
        CitizenHousingStatus housingStatus = CitizenNeedsViews.extractHousingStatus(data);
        ObservedValue<CitizenEquipmentView> equipment = CitizenNeedsViews.extractEquipment(data, snapshotGameTime);
        BuilderActivityStatus builderActivity = CitizenWorkViews.extractBuilderActivity(data, workState, requestSnapshot.observation());
        CitizenVerifiedFactsView verifiedFacts = new CitizenVerifiedFactsView(
                snapshotGameTime,
                observedHealth,
                equipment,
                housingStatus,
                requestSnapshot.observation(),
                builderActivity
        );

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
                data.getUUID(),
                contextPlayerId,
                new CitizenIdentityView(
                        data.getName(), data.isChild(), data.isFemale(), isGuard,
                        personalityView, customPersonalityText
                ),
                new CitizenFamilyView(parents, data.getPartner() != null, childrenNames, siblingNames),
                new CitizenWellbeingView(
                        data.getCitizenDiseaseHandler().isSick(),
                        housingStatus == CitizenHousingStatus.HOMELESS,
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
                ColonyPromptViewFactory.createColonyView(data.getColony(), data.getEntity().map(entity -> entity.level()).orElse(null)),
                new ConversationPromptView(
                        getLanguageNameFromCode(McTalkingConfig.INSTANCE.instance().language),
                        relation,
                        playerState,
                        interestedParties
                ),
                activity,
                verifiedFacts,
                memorySnapshot,
                null
        );
    }

    /**
     * A visitor (tavern guest) has no job, home, family, requests or quests in the colony, so those
     * stay empty; happiness modifiers such as homelessness do not apply to a guest either.
     */
    private static CitizenPromptView createVisitor(
            IVisitorData data,
            @NotNull Map<UUID, String> interestedParties,
            @Nullable ServerPlayer speakingTo,
            @Nullable UUID contextPlayerId
    ) {
        long snapshotGameTime = data.getColony().getWorld() == null ? -1L : data.getColony().getWorld().getGameTime();
        ObservedValue<Double> observedHealth = CitizenNeedsViews.extractObservedHealth(data, snapshotGameTime);
        var personalityExt = (CitizenDataPersonalityExtended) data;
        personalityExt.mc_talking$assignPersonality();
        PersonalityArchetype personality = personalityExt.mc_talking$getPersonality();
        CitizenPersonalityView personalityView = personality == null ? null : new CitizenPersonalityView(
                personality.name().toLowerCase(Locale.ROOT), personality.getPromptLines(), false);
        var memoryData = (CitizenDataMemoryExtended) data;
        var memory = memoryData.mc_talking$getOrInitializeMemory();
        int daysInColony = memory.visitorDays(data.getColony().getDay());

        var equipment = CitizenNeedsViews.extractEquipment(data, snapshotGameTime);
        var verifiedFacts = new CitizenVerifiedFactsView(
                snapshotGameTime,
                observedHealth,
                equipment,
                CitizenHousingStatus.UNKNOWN,
                ObservedValue.current(new CitizenRequestAvailabilityView(List.of(), List.of()), snapshotGameTime),
                BuilderActivityStatus.NOT_BUILDER
        );
        String nameTagDescription = extractNameTagDescription(data);
        var activity = new CitizenActivityView(
                CitizenActivityCategory.OTHER, null, null, null, null,
                "visiting the colony's tavern", nameTagDescription, List.of());

        return new CitizenPromptSnapshot(
                data.getUUID(),
                contextPlayerId,
                new CitizenIdentityView(data.getName(), data.isChild(), data.isFemale(), false,
                        personalityView, personalityExt.mc_talking$getCustomPersonality()),
                new CitizenFamilyView(List.of(), false, List.of(), List.of()),
                new CitizenWellbeingView(
                        data.getCitizenDiseaseHandler().isSick(),
                        false,
                        data.getSaturation(),
                        observedHealth.value(),
                        data.getCitizenHappinessHandler().getHappiness(data.getColony(), data),
                        List.of(),
                        false,
                        List.of(),
                        null
                ),
                new CitizenWorkView(null, null, null, CitizenWorkViews.extractSkills(data), List.of(), List.of(), List.of()),
                ColonyPromptViewFactory.createColonyView(data.getColony(), data.getEntity().map(entity -> entity.level()).orElse(null)),
                new ConversationPromptView(
                        getLanguageNameFromCode(McTalkingConfig.INSTANCE.instance().language),
                        CitizenFamilyViews.extractPlayerRelation(data, speakingTo),
                        extractPlayerState(speakingTo),
                        interestedParties
                ),
                activity,
                verifiedFacts,
                MemorySnapshotFactory.create(memory),
                new VisitorPromptView(describeRecruitCost(data.getRecruitCost()), daysInColony)
        );
    }

    @Nullable
    private static String describeRecruitCost(@Nullable ItemStack cost) {
        if (cost == null || cost.isEmpty()) return null;
        return cost.getCount() + " x " + cost.getHoverName().getString();
    }

    // ── Extracted helpers ────────────────────────────────────────────────

    @Nullable
    private static String extractNameTagDescription(ICitizenData data) {
        if (data.getJob() == null) return null;
        return data.getJob().getNameTagDescription();
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

    public static String getLanguageNameFromCode(String localeCode) {
        try {
            String[] parts = localeCode.split("-");
            String languageCode = parts[0];
            Locale locale = Locale.forLanguageTag(languageCode);
            return locale.getDisplayLanguage(Locale.ENGLISH);
        } catch (Exception e) {
            return localeCode;
        }
    }
}
