package me.sshcrack.mc_talking.manager.prompt;

import com.minecolonies.api.colony.IVisitorData;
import me.sshcrack.mc_talking.api.prompt.view.BuilderActivityStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityCategory;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenFamilyView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenHousingStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenIdentityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPersonalityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenRequestAvailabilityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenVerifiedFactsView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWellbeingView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWorkView;
import me.sshcrack.mc_talking.api.prompt.view.ConversationPromptView;
import me.sshcrack.mc_talking.api.prompt.view.ObservedValue;
import me.sshcrack.mc_talking.api.prompt.view.VisitorPromptView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.PersonalityArchetype;
import me.sshcrack.mc_talking.conversations.memory.MemorySnapshotFactory;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.duck.CitizenDataPersonalityExtended;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** The prompt view of a tavern visitor, who is not a colony citizen yet. */
public final class VisitorPromptViews {
    private VisitorPromptViews() {
    }

    /**
     * A visitor (tavern guest) has no job, home, family, requests or quests in the colony, so those
     * stay empty; happiness modifiers such as homelessness do not apply to a guest either.
     */
    public static CitizenPromptView create(
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
        String nameTagDescription = CitizenPromptViewFactory.extractNameTagDescription(data);
        var activity = new CitizenActivityView(
                CitizenActivityCategory.OTHER, null, null, null, null,
                "visiting the colony's tavern", nameTagDescription, List.of());

        return CitizenPromptViewFactory.snapshot(
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
                        CitizenPromptViewFactory.getLanguageNameFromCode(McTalkingConfig.INSTANCE.instance().language),
                        CitizenFamilyViews.extractPlayerRelation(data, speakingTo),
                        PlayerStateViews.describe(speakingTo),
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
}
