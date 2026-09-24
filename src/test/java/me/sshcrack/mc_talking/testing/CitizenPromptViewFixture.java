package me.sshcrack.mc_talking.testing;

import me.sshcrack.mc_talking.api.memory.CitizenMemoryEntryView;
import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipView;
import me.sshcrack.mc_talking.api.memory.MemoryEntryType;
import me.sshcrack.mc_talking.api.memory.MemoryProvenance;
import me.sshcrack.mc_talking.api.prompt.view.BuilderActivityStatus;
import me.sshcrack.mc_talking.api.prompt.view.BuildingView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityCategory;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenEquipmentView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenFamilyView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenHousingStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenIdentityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPersonalityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenRequestAvailabilityView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenSkill;
import me.sshcrack.mc_talking.api.prompt.view.CitizenVerifiedFactsView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWellbeingView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenWorkView;
import me.sshcrack.mc_talking.api.prompt.view.ColonyFoodSituation;
import me.sshcrack.mc_talking.api.prompt.view.ColonyPromptView;
import me.sshcrack.mc_talking.api.prompt.view.ConversationPromptView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.api.prompt.view.VisitorPromptView;
import me.sshcrack.mc_talking.api.prompt.view.ObservedValue;
import me.sshcrack.mc_talking.api.prompt.view.PlayerRelationView;
import me.sshcrack.mc_talking.api.prompt.view.SkillLevelView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Builds realistic {@link CitizenPromptView}s for prompt tests.
 *
 * <p>Defaults describe a housed, moderately content farmer talking to the colony owner, with a few
 * memories and happiness modifiers so every prompt section renders. Each view group can be replaced
 * wholesale, and the common knobs (language, housing, job, happiness, ...) have shortcuts. All
 * values are fixed, so the rendered prompt is reproducible.</p>
 */
public final class CitizenPromptViewFixture {
    public static final UUID CITIZEN_ID = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    public static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    public static final long GAME_TIME = 240_000L;

    private UUID citizenId = CITIZEN_ID;
    private @Nullable UUID playerId = PLAYER_ID;
    private CitizenIdentityView identity = new CitizenIdentityView(
            "Maria Silva", false, true, false,
            new CitizenPersonalityView("cheerful", "You are warm, chatty and quick to laugh.", false),
            null);
    private CitizenFamilyView family = new CitizenFamilyView(
            List.of("Ana Silva"), false, List.of(), List.of("Tomas Silva"));
    private CitizenWellbeingView wellbeing = new CitizenWellbeingView(
            false, false, 12.0, 90.0, 6.5,
            List.of(new HappinessModifierView(HappinessModifierType.HOMELESSNESS, 0.5),
                    new HappinessModifierView(HappinessModifierType.FOOD, 1.0)),
            true, List.of(), ColonyFoodSituation.STAFFED_RESTAURANT);
    private CitizenWorkView work = new CitizenWorkView(
            "Farmer",
            new BuildingView("Residence", 1),
            new BuildingView("Farm", 2),
            List.of(new SkillLevelView(CitizenSkill.STAMINA, 14),
                    new SkillLevelView(CitizenSkill.ATHLETICS, 9),
                    new SkillLevelView(CitizenSkill.KNOWLEDGE, 3)),
            List.of(), List.of(), List.of());
    private ColonyPromptView colony = new ColonyPromptView(
            1, "Riverside", false, "Steve", 12, null, 0, GAME_TIME,
            List.of("A new bakery was built"), List.of(), null, null);
    private ConversationPromptView conversation = new ConversationPromptView(
            "English",
            new PlayerRelationView("Steve", "Owner", false, true),
            null,
            Map.of(PLAYER_ID, "Steve"));
    private CitizenActivityView activity = new CitizenActivityView(
            CitizenActivityCategory.WORKING, null, null, null, null,
            "harvesting wheat at the farm", null,
            List.of("Planted carrots", "Delivered wheat to the warehouse"));
    private CitizenVerifiedFactsView verifiedFacts = new CitizenVerifiedFactsView(
            GAME_TIME,
            ObservedValue.current(90.0, GAME_TIME),
            ObservedValue.current(new CitizenEquipmentView(List.of("Leather cap"), List.of("Iron hoe")), GAME_TIME),
            CitizenHousingStatus.HOUSED,
            ObservedValue.current(new CitizenRequestAvailabilityView(List.of(), List.of()), GAME_TIME),
            BuilderActivityStatus.NOT_BUILDER);
    private @Nullable CitizenMemorySnapshot memories = new CitizenMemorySnapshot(
            List.of(), List.of(),
            List.of(new CitizenMemoryEntryView(MemoryEntryType.EVENT, "Steve helped me rebuild the fence",
                    MemoryProvenance.OBSERVED_EVENT, PLAYER_ID, null, null)),
            List.of(new CitizenRelationshipView(PLAYER_ID, CitizenRelationshipDimension.FRIENDLINESS, 0.6f)),
            List.of(), List.of(), List.of(),
            "Maria has lived in Riverside since it was founded.");

    private @Nullable VisitorPromptView visitor;

    private CitizenPromptViewFixture() {
    }

    public static CitizenPromptViewFixture citizen() {
        return new CitizenPromptViewFixture();
    }

    /** A second, distinct default citizen for multi-participant prompts. */
    public static CitizenPromptViewFixture secondCitizen() {
        return citizen()
                .citizenId(UUID.fromString("00000000-0000-0000-0000-00000000c002"))
                .identity(new CitizenIdentityView("Joao Costa", false, false, false,
                        new CitizenPersonalityView("grumpy", "You are blunt and complain about the weather.", false),
                        null))
                .job("Builder", new BuildingView("Builder's Hut", 1))
                .happiness(4.0)
                .memories(null);
    }

    public CitizenPromptViewFixture citizenId(UUID citizenId) {
        this.citizenId = citizenId;
        return this;
    }

    public CitizenPromptViewFixture playerId(@Nullable UUID playerId) {
        this.playerId = playerId;
        return this;
    }

    public CitizenPromptViewFixture identity(CitizenIdentityView identity) {
        this.identity = identity;
        return this;
    }

    public CitizenPromptViewFixture family(CitizenFamilyView family) {
        this.family = family;
        return this;
    }

    public CitizenPromptViewFixture wellbeing(CitizenWellbeingView wellbeing) {
        this.wellbeing = wellbeing;
        return this;
    }

    public CitizenPromptViewFixture work(CitizenWorkView work) {
        this.work = work;
        return this;
    }

    public CitizenPromptViewFixture colony(ColonyPromptView colony) {
        this.colony = colony;
        return this;
    }

    public CitizenPromptViewFixture conversation(ConversationPromptView conversation) {
        this.conversation = conversation;
        return this;
    }

    public CitizenPromptViewFixture activity(CitizenActivityView activity) {
        this.activity = activity;
        return this;
    }

    public CitizenPromptViewFixture verifiedFacts(CitizenVerifiedFactsView verifiedFacts) {
        this.verifiedFacts = verifiedFacts;
        return this;
    }

    public CitizenPromptViewFixture memories(@Nullable CitizenMemorySnapshot memories) {
        this.memories = memories;
        return this;
    }

    public CitizenPromptViewFixture language(String responseLanguageName) {
        var c = conversation;
        conversation = new ConversationPromptView(responseLanguageName, c.playerRelation(), c.playerState(),
                c.interestedParties());
        return this;
    }

    /** No player context, as for citizen-to-citizen and pregenerated lines. */
    public CitizenPromptViewFixture withoutPlayer() {
        playerId = null;
        conversation = new ConversationPromptView(conversation.responseLanguageName(), null, null, Map.of());
        return this;
    }

    public CitizenPromptViewFixture home(@Nullable BuildingView home) {
        var w = work;
        work = new CitizenWorkView(w.jobName(), home, w.workplace(), w.skills(),
                w.assignedOrInProgressItemRequests(), w.waitingForResolverItemRequests(), w.activeQuests());
        return housing(home == null ? CitizenHousingStatus.HOMELESS : CitizenHousingStatus.HOUSED);
    }

    public CitizenPromptViewFixture job(@Nullable String jobName, @Nullable BuildingView workplace) {
        var w = work;
        work = new CitizenWorkView(jobName, w.home(), workplace, w.skills(),
                w.assignedOrInProgressItemRequests(), w.waitingForResolverItemRequests(), w.activeQuests());
        return this;
    }

    public CitizenPromptViewFixture colonyAgeDays(int ageDays) {
        var c = colony;
        colony = new ColonyPromptView(c.id(), c.name(), c.peaceful(), c.foundingPlayer(), ageDays,
                c.lastRaidEndTimeTicks(), c.lastRaidLostCitizens(), c.currentGameTimeTicks(), c.recentEvents(),
                c.connections(), c.milestone(), c.environment());
        return this;
    }

    /** No home: clears the home building and marks the citizen homeless everywhere the prompt looks. */
    public CitizenPromptViewFixture homeless() {
        home(null);
        return wellbeing(w -> new CitizenWellbeingView(w.sick(), true, w.saturation(), w.healthPercent(),
                w.happiness(), w.happinessModifiers(), w.hasSchool(), w.blockingInteractionMessages(), w.foodSituation()));
    }

    public CitizenPromptViewFixture happiness(double happiness) {
        return wellbeing(w -> new CitizenWellbeingView(w.sick(), w.homeless(), w.saturation(), w.healthPercent(),
                happiness, w.happinessModifiers(), w.hasSchool(), w.blockingInteractionMessages(), w.foodSituation()));
    }

    public CitizenPromptViewFixture happinessModifiers(HappinessModifierView... modifiers) {
        return wellbeing(w -> new CitizenWellbeingView(w.sick(), w.homeless(), w.saturation(), w.healthPercent(),
                w.happiness(), List.of(modifiers), w.hasSchool(), w.blockingInteractionMessages(), w.foodSituation()));
    }

    public CitizenPromptViewFixture wellbeing(UnaryOperator<CitizenWellbeingView> change) {
        wellbeing = change.apply(wellbeing);
        return this;
    }

    public CitizenPromptViewFixture interestedParty(UUID id, String name) {
        var parties = new LinkedHashMap<>(conversation.interestedParties());
        parties.put(id, name);
        conversation = new ConversationPromptView(conversation.responseLanguageName(),
                conversation.playerRelation(), conversation.playerState(), parties);
        return this;
    }

    private CitizenPromptViewFixture housing(CitizenHousingStatus status) {
        var f = verifiedFacts;
        verifiedFacts = new CitizenVerifiedFactsView(f.capturedAtGameTime(), f.healthPercent(), f.equipment(),
                status, f.requests(), f.builderActivity());
        var w = wellbeing;
        wellbeing = new CitizenWellbeingView(w.sick(), status == CitizenHousingStatus.HOMELESS, w.saturation(),
                w.healthPercent(), w.happiness(), w.happinessModifiers(), w.hasSchool(),
                w.blockingInteractionMessages(), w.foodSituation());
        return this;
    }

    public CitizenPromptView build() {
        return new FixtureView(citizenId, playerId, identity, family, wellbeing, work, colony, conversation,
                activity, verifiedFacts, memories, visitor);
    }

    /** A tavern visitor: no job, home or family, with the given visitor facts. */
    public CitizenPromptViewFixture visitor(VisitorPromptView visitor) {
        this.visitor = visitor;
        family = new CitizenFamilyView(List.of(), false, List.of(), List.of());
        job(null, null);
        home(null);
        var f = verifiedFacts;
        verifiedFacts = new CitizenVerifiedFactsView(f.capturedAtGameTime(), f.healthPercent(), f.equipment(),
                CitizenHousingStatus.UNKNOWN, f.requests(), f.builderActivity());
        return wellbeing(w -> new CitizenWellbeingView(w.sick(), false, w.saturation(), w.healthPercent(),
                w.happiness(), List.of(), false, List.of(), null));
    }

    private record FixtureView(
            @NotNull UUID citizenId,
            @Nullable UUID playerId,
            @NotNull CitizenIdentityView identity,
            @NotNull CitizenFamilyView family,
            @NotNull CitizenWellbeingView wellbeing,
            @NotNull CitizenWorkView work,
            @NotNull ColonyPromptView colony,
            @NotNull ConversationPromptView conversation,
            @NotNull CitizenActivityView activity,
            @NotNull CitizenVerifiedFactsView verifiedFacts,
            @Nullable CitizenMemorySnapshot memories,
            @Nullable VisitorPromptView visitor
    ) implements CitizenPromptView {
    }
}
