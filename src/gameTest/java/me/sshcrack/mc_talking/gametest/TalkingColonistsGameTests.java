package me.sshcrack.mc_talking.gametest;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.core.colony.jobs.JobBuilder;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.context.CitizenContextService;
import me.sshcrack.mc_talking.api.conversation.ConversationEligibility;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.prompt.view.CitizenHousingStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
/*? if neoforge {*/
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
/*?}*/
/*? if forge {*/
/*import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
*//*?}*/

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
