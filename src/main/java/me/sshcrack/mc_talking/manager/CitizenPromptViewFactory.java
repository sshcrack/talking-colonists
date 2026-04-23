package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.util.Tuple;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.api.prompt.view.PlayerRelationView;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import me.sshcrack.mc_talking.mixin.CitizenDataAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/**
 * Builds stable API prompt views from MineColonies runtime data.
 */
public final class CitizenPromptViewFactory {

    private CitizenPromptViewFactory() {
    }

    public static CitizenPromptView create(ICitizenData data, ServerPlayer speakingTo) {
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
                return new HappinessModifierView(modifierId, modifier.getFactor(data));
            })
            .filter(m -> m != null)
            .collect(Collectors.toList());

        boolean hasSchool = data.getColony().getServerBuildingManager().hasBuilding(
            ModBuildings.school.get().getRegistryName(),
            1,
            true
        );

        List<SkillLevelView> skills = List.of();
        if (data.getCitizenSkillHandler() != null) {
            skills = data.getCitizenSkillHandler().getSkills().entrySet().stream()
                .map(e -> new SkillLevelView(e.getKey().name(), e.getValue().getLevel()))
                .collect(Collectors.toList());
        }

        List<String> blockingMessages = ((CitizenDataAccessor) data)
            .getCitizenChatOptions()
            .values()
            .stream()
            .filter(e -> e.getPriority().getPriority() >= ChatPriority.IMPORTANT.getPriority())
            .map(e -> e.getInquiry().getString())
            .collect(Collectors.toList());

        PlayerRelationView relation = null;
        var perms = data.getColony().getPermissions().getPlayers().get(speakingTo.getUUID());
        if (perms != null) {
            var rank = perms.getRank();
            String rankName = rank.isHostile() ? "enemy" : (
                rank.isColonyManager() ? "manager" : (
                    rank.isInitial() ? "leader" : "visitor"
                )
            );
            relation = new PlayerRelationView(rankName, rank.isHostile(), rank.isColonyManager() || rank.isInitial());
        }

        return new CitizenPromptView(
            data.getName(),
            data.isChild(),
            data.isFemale(),
            jobName,
            data.getCitizenDiseaseHandler().isSick(),
            data.getHomeBuilding() == null,
            parents,
            data.getPartner() != null,
            data.getChildren() != null ? data.getChildren().size() : 0,
            data.getSiblings() != null ? data.getSiblings().size() : 0,
            data.getSaturation(),
            healthPercent,
            createStatusView(data.getStatus(), data),
            happiness,
            modifiers,
            hasSchool,
            skills,
            blockingMessages,
            relation,
            getLanguageNameFromCode(CONFIG.language.get())
        );
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
}
