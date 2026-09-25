package me.sshcrack.mc_talking.gametest;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.core.colony.jobs.JobBuilder;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.colony.AddonColonyEvent;
import me.sshcrack.mc_talking.api.colony.ColonyEventService;
import me.sshcrack.mc_talking.api.colony.ColonyEventView;
import me.sshcrack.mc_talking.api.context.CitizenContextService;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationRules;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationService;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationOptions;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ConversationEligibility;
import me.sshcrack.mc_talking.api.conversation.ConversationUtteranceEvent;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.memory.BroadcastRequest;
import me.sshcrack.mc_talking.api.memory.BroadcastSource;
import me.sshcrack.mc_talking.api.memory.CitizenMemoryService;
import me.sshcrack.mc_talking.api.memory.MemoryProvenance;
import me.sshcrack.mc_talking.api.prompt.view.CitizenHousingStatus;
import me.sshcrack.mc_talking.config.ConfigPreset;
import me.sshcrack.mc_talking.config.ConfigPresets;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.construction.ConstructionContext;
import me.sshcrack.mc_talking.conversations.construction.ConstructionPrompts;
import me.sshcrack.mc_talking.conversations.construction.ConstructionSite;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
/*? if neoforge {*/
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
/*?}*/
/*? if forge {*/
/*import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
*//*?}*/

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Headless server GameTests for server-side Talking Colonists behaviour backed by real MineColonies
 * state. Run with {@code ./gradlew :<version>:runGameTestServer}; see
 * {@code docs/automated-verification.md}.
 *
 * <p>Every test uses its own batch so its colony is created and deleted before the next test's
 * colony exists (MineColonies resolves buildings by colony claim, which must not overlap).</p>
 */
@GameTestHolder(McTalking.MODID)
@PrefixGameTestTemplate(false)
public final class TalkingColonistsGameTests {
    /** 9x4x9 stone floor; test-only resource in {@code src/gameTest/resources}. */
    private static final String FLOOR = "empty_floor";
    /**
     * The runner force-loads the test chunks, but the tickets apply a tick later. Until then a new
     * entity is not in the level's entity lookup and MineColonies discards the citizen on registration.
     */
    private static final long SETUP_TICKS = 20;

    private TalkingColonistsGameTests() {
    }

    /** The public prompt snapshot reflects real MineColonies housing and job assignments. */
    @GameTest(template = FLOOR, batch = "mc_talking_prompt_view", setupTicks = SETUP_TICKS, timeoutTicks = 100)
    public static void promptViewReflectsHousingAndJob(GameTestHelper helper) {
        logFailures("promptViewReflectsHousingAndJob", () -> {
            try (var fixture = ColonyTestHarness.create(helper)) {
                var citizen = fixture.spawnCitizen(new BlockPos(4, 1, 4));

                var before = CitizenContextService.snapshot(citizen);
                helper.assertTrue(before.verifiedFacts().housingStatus() == CitizenHousingStatus.HOMELESS,
                        "fresh citizen should be HOMELESS, was " + before.verifiedFacts().housingStatus());
                helper.assertTrue(before.work().home() == null, "fresh citizen should have no home view");
                helper.assertTrue(before.work().jobName() == null, "fresh citizen should be unemployed, was " + before.work().jobName());

                var residence = fixture.placeHut(ModBlocks.blockHutHome, new BlockPos(1, 1, 1), 1);
                var builderHut = fixture.placeHut(ModBlocks.blockHutBuilder, new BlockPos(7, 1, 7), 1);
                fixture.assignHome(residence, citizen);
                fixture.hireAsBuilder(builderHut, citizen);

                var data = ColonyTestHarness.data(citizen);
                helper.assertTrue(data.getHomeBuilding() == residence, "MineColonies did not record the home assignment");
                helper.assertTrue(data.getJob() instanceof JobBuilder, "MineColonies did not assign the builder job");

                var after = CitizenContextService.snapshot(citizen);
                helper.assertTrue(after.verifiedFacts().housingStatus() == CitizenHousingStatus.HOUSED,
                        "housed citizen should be HOUSED, was " + after.verifiedFacts().housingStatus());
                helper.assertTrue(after.work().home() != null && after.work().home().level() == 1,
                        "home view should describe the level-1 residence, was " + after.work().home());
                helper.assertTrue(after.work().workplace() != null && after.work().workplace().level() == 1,
                        "workplace view should describe the level-1 builder hut, was " + after.work().workplace());
                String expectedJob = Component.translatable(ModJobs.builder.get().getTranslationKey()).getString();
                helper.assertTrue(Objects.equals(after.work().jobName(), expectedJob),
                        "job name should be '" + expectedJob + "', was '" + after.work().jobName() + "'");
            }
        });
        helper.succeed();
    }

    /** Every citizen's prompt knows the colony's building jobs: a residence upgrade taken on by the builder. */
    @GameTest(template = FLOOR, batch = "mc_talking_construction", setupTicks = SETUP_TICKS, timeoutTicks = 100)
    public static void promptKnowsTheColonysBuildingJobs(GameTestHelper helper) {
        logFailures("promptKnowsTheColonysBuildingJobs", () -> {
            try (var fixture = ColonyTestHarness.create(helper)) {
                var builder = fixture.spawnCitizen(new BlockPos(4, 1, 4));
                var neighbour = fixture.spawnCitizen(new BlockPos(5, 1, 4));
                var residence = fixture.placeHut(ModBlocks.blockHutHome, new BlockPos(1, 1, 1), 1);
                var builderHut = fixture.placeHut(ModBlocks.blockHutBuilder, new BlockPos(7, 1, 7), 5);
                fixture.hireAsBuilder(builderHut, builder);

                // Huts placed by the harness have no blueprint; MineColonies names the next level's after it.
                residence.setBlueprintPath("/fundamentals/residence1.blueprint");
                residence.requestUpgrade(fixture.owner(), builderHut.getPosition());
                helper.assertTrue(!fixture.colony().getWorkManager().getWorkOrders().isEmpty(),
                        "MineColonies placed no work order for the residence upgrade");

                var view = CitizenContextService.snapshot(neighbour);
                helper.assertTrue(view instanceof ConstructionContext.Holder holder && holder.construction() != null,
                        "the prompt view carries no building jobs");
                var sites = ((ConstructionContext.Holder) view).construction().sites();
                helper.assertTrue(sites.size() == 1, "expected one building job, got " + sites);
                var site = sites.get(0);
                helper.assertTrue(site.kind() == ConstructionSite.Kind.UPGRADE && site.level() == 2,
                        "expected an upgrade to level 2, got " + site);
                helper.assertTrue(ColonyTestHarness.data(builder).getName().equals(site.builder()),
                        "expected the builder to have taken it on, got " + site);
                McTalking.LOGGER.info("Construction prompt line: {}", ConstructionPrompts.line(site, null));
            }
        });
        helper.succeed();
    }

    /** Conversation eligibility rejects a citizen MineColonies has put to sleep in a bed. */
    @GameTest(template = FLOOR, batch = "mc_talking_eligibility_sleep", setupTicks = SETUP_TICKS, timeoutTicks = 100)
    public static void eligibilityRejectsSleepingCitizen(GameTestHelper helper) {
        logFailures("eligibilityRejectsSleepingCitizen", () -> {
            try (var fixture = ColonyTestHarness.create(helper)) {
                var citizen = fixture.spawnCitizen(new BlockPos(4, 1, 4));

                // Control: the same awake citizen is eligible, so the rejection below is caused by sleep.
                var awake = ConversationManager.conversationEligibility(citizen, ConversationKind.PLAYER);
                helper.assertTrue(awake.eligible(), "awake citizen should be eligible, was " + awake);

                fixture.sleepInBed(citizen, new BlockPos(2, 1, 5));
                helper.assertTrue(citizen.isSleeping(), "fixture precondition: citizen should be asleep");

                for (ConversationKind kind : ConversationKind.values()) {
                    var asleep = ConversationManager.conversationEligibility(citizen, kind);
                    helper.assertTrue(asleep.status() == ConversationEligibility.Status.SLEEPING,
                            "sleeping citizen must be rejected as SLEEPING for " + kind + ", was " + asleep);
                }
            }
        });
        helper.succeed();
    }

    /** A1: an immediate broadcast reaches every real citizen's memory snapshot, and retraction removes it. */
    @GameTest(template = FLOOR, batch = "mc_talking_broadcast_publish", setupTicks = SETUP_TICKS, timeoutTicks = 100)
    public static void publishedBroadcastReachesEveryCitizen(GameTestHelper helper) {
        logFailures("publishedBroadcastReachesEveryCitizen", () -> {
            try (var fixture = ColonyTestHarness.create(helper)) {
                var first = fixture.spawnCitizen(new BlockPos(2, 1, 2));
                var second = fixture.spawnCitizen(new BlockPos(6, 1, 6));

                var result = CitizenMemoryService.publishBroadcast(fixture.colony(), BroadcastRequest.immediate(
                        BroadcastSource.addon("mc_talking_test", "the notice board"), "Harvest festival tonight"));
                helper.assertTrue(result.isPublished() && result.recipients() == 2, "expected 2 recipients, was " + result);

                for (var citizen : java.util.List.of(first, second)) {
                    var broadcasts = CitizenMemoryService.snapshot(citizen).orElseThrow().broadcasts();
                    helper.assertTrue(broadcasts.size() == 1, "citizen should remember 1 broadcast, had " + broadcasts);
                    var view = broadcasts.get(0);
                    helper.assertTrue(view.id().equals(result.broadcastId())
                                    && "the notice board".equals(view.sourceLabel())
                                    && view.provenance() == MemoryProvenance.ADDON_DIRECT_WRITE,
                            "unexpected broadcast view " + view);
                }

                helper.assertTrue(CitizenMemoryService.retractBroadcast(fixture.colony(), result.broadcastId()),
                        "retraction should report the broadcast was remembered");
                helper.assertTrue(CitizenMemoryService.snapshot(first).orElseThrow().broadcasts().isEmpty(),
                        "retracted broadcast should be forgotten");
            }
        });
        helper.succeed();
    }

    /** A2: an addon event reaches listeners, the public feed, and the citizen's prompt context. */
    @GameTest(template = FLOOR, batch = "mc_talking_colony_events", setupTicks = SETUP_TICKS, timeoutTicks = 100)
    public static void addonColonyEventReachesListenersFeedAndPrompt(GameTestHelper helper) {
        logFailures("addonColonyEventReachesListenersFeedAndPrompt", () -> {
            var heard = new java.util.ArrayList<ColonyEventView>();
            try (var fixture = ColonyTestHarness.create(helper);
                 var listener = ColonyEventService.registerListener("mc_talking_test:listener", 0,
                         (colony, event) -> { if (event.isAddonEvent()) heard.add(event); })) {
                var citizen = fixture.spawnCitizen(new BlockPos(4, 1, 4));

                helper.assertTrue(ColonyEventService.record(fixture.colony(),
                                new AddonColonyEvent("mc_talking_test", "election_won", "Maria won the mayoral election")),
                        "record should succeed on a loaded colony");

                helper.assertTrue(heard.size() == 1 && "election_won".equals(heard.get(0).addonKey()),
                        "listener should see exactly the addon event, saw " + heard);
                var recent = ColonyEventService.recent(fixture.colony(), java.time.Duration.ofMinutes(5));
                helper.assertTrue(recent.stream().anyMatch(e -> e.isAddonEvent()
                                && "mc_talking_test".equals(e.addonNamespace())),
                        "recent feed should contain the addon event, was " + recent);
                var promptEvents = CitizenContextService.snapshot(citizen).colony().recentEvents();
                helper.assertTrue(promptEvents.contains("Maria won the mayoral election"),
                        "citizen prompt context should include the addon event, was " + promptEvents);
            }
        });
        helper.succeed();
    }

    /** A8: visitors are rejected by default, and speak only for the kinds a visitor policy allows. */
    @GameTest(template = FLOOR, batch = "mc_talking_visitor_policy", setupTicks = SETUP_TICKS, timeoutTicks = 100)
    public static void visitorsSpeakOnlyWithAPolicy(GameTestHelper helper) {
        logFailures("visitorsSpeakOnlyWithAPolicy", () -> {
            try (var fixture = ColonyTestHarness.create(helper)) {
                var visitor = fixture.spawnVisitor(new BlockPos(4, 1, 4));

                for (ConversationKind kind : ConversationKind.values()) {
                    var rejected = ConversationManager.conversationEligibility(visitor, kind);
                    helper.assertTrue(rejected.status() == ConversationEligibility.Status.VISITOR,
                            "visitor must be rejected as VISITOR for " + kind + " without a policy, was " + rejected);
                }

                var registration = CitizenConversationRules.registerVisitorPolicy("mc_talking_test:tavern_talk", 0,
                        (guest, kind) -> kind == ConversationKind.PLAYER);
                try {
                    var player = ConversationManager.conversationEligibility(visitor, ConversationKind.PLAYER);
                    helper.assertTrue(player.eligible(), "policy should allow PLAYER conversations, was " + player);
                    var ambient = ConversationManager.conversationEligibility(visitor, ConversationKind.ADDON_AMBIENT);
                    helper.assertTrue(ambient.status() == ConversationEligibility.Status.VISITOR,
                            "kinds the policy does not allow stay rejected, was " + ambient);
                } finally {
                    registration.close();
                }
                var after = ConversationManager.conversationEligibility(visitor, ConversationKind.PLAYER);
                helper.assertTrue(after.status() == ConversationEligibility.Status.VISITOR,
                        "closing the policy restores the default rejection, was " + after);
            }
        });
        helper.succeed();
    }

    /** A8: a visitor's prompt view has visitor facts and none of the colonist-only fields. */
    @GameTest(template = FLOOR, batch = "mc_talking_visitor_view", setupTicks = SETUP_TICKS, timeoutTicks = 100)
    public static void visitorPromptViewHasNoColonistFields(GameTestHelper helper) {
        logFailures("visitorPromptViewHasNoColonistFields", () -> {
            try (var fixture = ColonyTestHarness.create(helper)) {
                var visitor = fixture.spawnVisitor(new BlockPos(4, 1, 4));
                var data = (IVisitorData) visitor.getCitizenData();
                data.setRecruitCosts(new ItemStack(Items.DIAMOND, 3));

                var view = CitizenContextService.snapshot(visitor);
                helper.assertTrue(view.visitor() != null, "visitor view missing");
                helper.assertTrue(view.visitor().recruitCost() != null && view.visitor().recruitCost().startsWith("3 x"),
                        "recruit cost should be described, was " + view.visitor().recruitCost());
                helper.assertTrue(view.work().jobName() == null && view.work().home() == null
                                && view.work().workplace() == null, "visitor must have no job/home/workplace: " + view.work());
                helper.assertTrue(view.work().assignedOrInProgressItemRequests().isEmpty()
                        && view.work().activeQuests().isEmpty(), "visitor must have no requests or quests");
                helper.assertTrue(view.family().parentNames().isEmpty() && !view.family().hasPartner()
                                && view.family().childNames().isEmpty() && view.family().siblingNames().isEmpty(),
                        "visitor must have no family: " + view.family());
                helper.assertTrue(view.wellbeing().happinessModifiers().isEmpty() && !view.wellbeing().homeless(),
                        "visitor must have no colonist happiness modifiers or homelessness");
                helper.assertTrue(view.verifiedFacts().housingStatus() == CitizenHousingStatus.UNKNOWN,
                        "visitor housing should be UNKNOWN, was " + view.verifiedFacts().housingStatus());
            }
        });
        helper.succeed();
    }

    /** A8: a recruited visitor keeps its short-term memory, the way MineColonies copies visitor data. */
    @GameTest(template = FLOOR, batch = "mc_talking_visitor_recruit", setupTicks = SETUP_TICKS, timeoutTicks = 100)
    public static void recruitedVisitorKeepsItsMemory(GameTestHelper helper) {
        logFailures("recruitedVisitorKeepsItsMemory", () -> {
            try (var fixture = ColonyTestHarness.create(helper)) {
                var visitor = fixture.spawnVisitor(new BlockPos(4, 1, 4));
                var visitorData = visitor.getCitizenData();
                var memory = ((CitizenDataMemoryExtended) visitorData).mc_talking$getOrInitializeMemory();
                memory.addFact("Steve promised me a bed if I stay");

                // MineColonies' RecruitmentInteraction creates a citizen and copies the visitor's NBT into it.
                var recruit = fixture.colony().getCitizenManager().createAndRegisterCivilianData();
                /*? if neoforge {*/
                var provider = helper.getLevel().registryAccess();
                recruit.deserializeNBT(provider, visitorData.serializeNBT(provider));
                /*?}*/
                /*? if forge {*/
                /*recruit.deserializeNBT(visitorData.serializeNBT());
                *//*?}*/

                var kept = ((CitizenDataMemoryExtended) recruit).mc_talking$getMemory();
                helper.assertTrue(kept != null && kept.getFacts().contains("Steve promised me a bed if I stay"),
                        "recruited citizen should keep the visitor's memory, had " + (kept == null ? null : kept.getFacts()));
            }
        });
        helper.succeed();
    }

    /** A5: a controlled session's player statement reaches utterance listeners as typed player input. */
    @GameTest(template = FLOOR, batch = "mc_talking_utterance_events", setupTicks = SETUP_TICKS, timeoutTicks = 100)
    public static void controlledPlayerStatementIsAnUtterance(GameTestHelper helper) {
        logFailures("controlledPlayerStatementIsAnUtterance", () -> {
            try (var fixture = ColonyTestHarness.create(helper)) {
                var citizen = fixture.spawnCitizen(new BlockPos(4, 1, 4));
                List<ConversationUtteranceEvent> heard = new ArrayList<>();
                var registration = CitizenConversationService.registerUtteranceListener(
                        "mc_talking_test:utterances", 0, heard::add);
                var session = CitizenConversationService.createControlledSession(helper.getLevel().getServer(),
                        List.of(citizen), "Harvest festival", ControlledConversationOptions.noAddonTools());
                try {
                    session.addPlayerStatement(fixture.owner(), "  Who brings the pumpkins?  ");
                    helper.assertTrue(heard.size() == 1, "expected one utterance, got " + heard);
                    var event = heard.get(0);
                    helper.assertTrue(event.kind() == ConversationKind.CONTROLLED
                                    && event.speaker() == ConversationUtteranceEvent.Speaker.PLAYER
                                    && event.source() == ConversationUtteranceEvent.Source.TYPED
                                    && fixture.owner().getUUID().equals(event.speakerId())
                                    && session.sessionId().equals(event.sessionId())
                                    && "Who brings the pumpkins?".equals(event.text()),
                            "unexpected utterance " + event);

                    session.end(ControlledConversationSession.EndReason.COMPLETED);
                    helper.assertTrue(heard.size() == 1, "no utterance after the session ended, got " + heard);
                } finally {
                    registration.close();
                    if (session.state() != ControlledConversationSession.State.ENDED) {
                        session.end(ControlledConversationSession.EndReason.COMPLETED);
                    }
                }
            }
        });
        helper.succeed();
    }

    /** Q8: a fresh config is the Free Tier preset, so new installs do not show Custom. */
    @GameTest(template = FLOOR, batch = "mc_talking_config_presets", timeoutTicks = 20)
    public static void freshConfigIsFreeTierPreset(GameTestHelper helper) {
        McTalkingConfig config = new McTalkingConfig();
        helper.assertTrue(config.configPreset == ConfigPreset.FREE_TIER, "default preset should be FREE_TIER");
        helper.assertTrue(ConfigPresets.matches(config, ConfigPreset.FREE_TIER),
                "field defaults should equal the Free Tier preset");
        helper.assertTrue(!ConfigPresets.reconcile(config), "a fresh config should need no preset change");
        helper.succeed();
    }

    /**
     * GameTest reports only the exception message; log the full stack so unexpected MineColonies
     * errors are diagnosable from the server log, then rethrow to fail the test.
     */
    private static void logFailures(String test, Runnable body) {
        try {
            body.run();
        } catch (GameTestAssertException assertion) {
            throw assertion;
        } catch (RuntimeException error) {
            McTalking.LOGGER.error("GameTest {} threw unexpectedly", test, error);
            throw error;
        }
    }

    // The Q4 ambient speech budget is intentionally not covered here: it only counts listeners in
    // the server player list, and a GameTest cannot add one (the loader fake player is not listed,
    // and a mock player's login breaks MineColonies' login sync). Its behaviour is covered by
    // AmbientSpeechBudgetRegistryTest (fake clock, multiple listeners, all-or-nothing charging).
}
