package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.util.Tuple;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusType;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.api.prompt.view.PlayerRelationView;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.mixin.CitizenDataAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/**
 * Builds stable API prompt views from MineColonies runtime data.
 */
public final class CitizenPromptViewFactory {

    private CitizenPromptViewFactory() {
    }

    public static CitizenPromptView create(ICitizenData data, @NotNull Map<UUID, String> interestedParties, @Nullable ServerPlayer speakingTo) {
        String jobName = null;
        if (data.getJob() != null) {
            jobName = Component.translatable(data.getJob().getJobRegistryEntry().getTranslationKey()).getString();
        }

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

        Double healthPercent = null;
        var entityOpt = data.getEntity();
        if (entityOpt.isPresent()) {
            var entity = entityOpt.get();
            healthPercent = (entity.getHealth() / Math.max(1.0, entity.getMaxHealth())) * 100.0;
        }

        var happinessHandler = data.getCitizenHappinessHandler();
        double happiness = happinessHandler.getHappiness(data.getColony(), data);

        List<HappinessModifierView> modifiers = happinessHandler.getModifiers().stream()
                .map(modifierId -> {
                    IHappinessModifier modifier = happinessHandler.getModifier(modifierId);
                    if (modifier == null) {
                        return null;
                    }
                    return new HappinessModifierView(resolveHappinessModifierType(modifierId), modifier.getFactor(data));
                })
                .filter(Objects::nonNull)
                .toList();

        boolean hasSchool = data.getColony().getServerBuildingManager().hasBuilding(
                ModBuildings.school.get().getRegistryName(),
                1,
                true
        );

        List<SkillLevelView> skills = List.of();
        if (data.getCitizenSkillHandler() != null) {
            skills = data.getCitizenSkillHandler().getSkills().entrySet().stream()
                    .map(e -> new SkillLevelView(e.getKey().name(), e.getValue().getLevel()))
                    .toList();
        }

        List<String> blockingMessages = ((CitizenDataAccessor) data)
                .getCitizenChatOptions()
                .values()
                .stream()
                .filter(e -> e.getPriority().getPriority() >= ChatPriority.IMPORTANT.getPriority())
                .map(e -> e.getInquiry().getString())
                .toList();

        PlayerRelationView relation = null;
        if (speakingTo != null) {
            String speakingName = speakingTo.getName().getString();
            var perms = data.getColony().getPermissions().getPlayers().get(speakingTo.getUUID());
            if (perms != null) {
                var rank = perms.getRank();
                String rankName = getRankName(rank);
                relation = new PlayerRelationView(speakingName, rankName, rank.isHostile(), rank.isColonyManager() || rank.isInitial());
            }
        }

        List<String> childrenNames = new ArrayList<>();
        if (data.getChildren() != null) {
            for (int childId : data.getChildren()) {
                var child = data.getColony().getCitizen(childId);
                childrenNames.add(child.getName());
            }
        }
        List<String> siblingNames = new ArrayList<>();
        if (data.getSiblings() != null) {
            for (int siblingId : data.getSiblings()) {
                var sibling = data.getColony().getCitizen(siblingId);
                siblingNames.add(sibling.getName());
            }
        }

        String colonyName = data.getColony().getName();

        IBuilding homeBuilding = data.getHomeBuilding();
        String homeBuildingDisplayName = homeBuilding != null ? homeBuilding.getBuildingDisplayName() : null;
        int homeBuildingLevel = homeBuilding != null ? homeBuilding.getBuildingLevel() : 0;

        IBuilding workBuilding = data.getWorkBuilding();
        String workBuildingDisplayName = workBuilding != null ? workBuilding.getBuildingDisplayName() : null;
        int workBuildingLevel = workBuilding != null ? workBuilding.getBuildingLevel() : 0;

        return new CitizenPromptView(
                data.getName(),
                data.isChild(),
                data.isFemale(),
                jobName,
                data.getCitizenDiseaseHandler().isSick(),
                data.getHomeBuilding() == null,
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
                getLanguageNameFromCode(CONFIG.language.get()),
                ((CitizenDataMemoryExtended) data).mc_talking$getMemory(),
                interestedParties,
                colonyName,
                homeBuildingDisplayName,
                homeBuildingLevel,
                workBuildingDisplayName,
                workBuildingLevel
        );
    }

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
}
