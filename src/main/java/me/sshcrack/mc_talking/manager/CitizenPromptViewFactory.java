package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
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
import me.sshcrack.mc_talking.config.PersonalityArchetype;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.duck.CitizenDataPersonalityExtended;
import me.sshcrack.mc_talking.mixin.CitizenDataAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
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
import java.util.UUID;
import java.util.function.Consumer;

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

import me.sshcrack.mc_talking.config.McTalkingConfig;

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

        // Personality — lazy assignment on first prompt generation
        var personalityExt = (CitizenDataPersonalityExtended) data;
        personalityExt.mc_talking$assignPersonality();
        PersonalityArchetype personality = personalityExt.mc_talking$getPersonality();
        String customPersonalityText = personalityExt.mc_talking$getCustomPersonality();

        // ── Player state ──────────────────────────────────────────────────────
        String playerState = null;
        if (speakingTo != null) {
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
                    }

                    wornPieces++;
                }

                ps.append(", wearing ").append(wornPieces).append(" armor pieces");
                if (!bestArmorName.isEmpty())
                    ps.append(String.format(" (best: %s)", bestArmorName));
            } else {
                ps.append(", no armor");
            }
            playerState = ps.toString();
        }

        // ── Environment ────────────────────────────────────────────────────────
        String environment = null;
        var entityOptEnv = data.getEntity();
        if (entityOptEnv.isPresent()) {
            Level level = entityOptEnv.get().level();
            long dayTime = level.getDayTime() % 24000L;
            environment = "It is " + describeTime(dayTime) + " and " + describeWeather(level) + ".";
        }

        // ── Active item requests ──────────────────────────────────────────────
        List<String> activeItemRequests = null;
        if (workBuilding != null) {
            Collection<IRequest<?>> openRequests = workBuilding.getOpenRequests(data.getId());
            if (openRequests != null && !openRequests.isEmpty()) {
                activeItemRequests = new ArrayList<>();
                for (IRequest<?> request : openRequests) {
                    var requestable = request.getRequest();
                    if (requestable instanceof Stack stackReq) {
                        ItemStack itemStack = stackReq.getStack();
                        int count = stackReq.getCount();
                        activeItemRequests.add(count + "x " + itemStack.getDisplayName().getString());
                    } else {
                        activeItemRequests.add(request.getShortDisplayString().getString());
                    }
                }
            }
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
                personality,
                customPersonalityText,
                playerState,
                environment,
                activeItemRequests
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

    private static String describeTime(long dayTime) {
        if (dayTime < 1000) return "early morning (sunrise)";
        if (dayTime < 6000) return "morning";
        if (dayTime < 9000) return "midday";
        if (dayTime < 12000) return "afternoon";
        if (dayTime < 13000) return "sunset";
        if (dayTime < 18000) return "night";
        if (dayTime < 22000) return "late night";
        return "pre-dawn";
    }

    private static String describeWeather(Level level) {
        if (level.isThundering()) return "thundering";
        if (level.isRaining()) return "rainy";
        return "clear";
    }
}
