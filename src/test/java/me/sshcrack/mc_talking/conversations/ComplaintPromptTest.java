package me.sshcrack.mc_talking.conversations;

import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.ColonyPromptView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import me.sshcrack.mc_talking.testing.CitizenPromptViewFixture;
import me.sshcrack.mc_talking.testing.TestPromptProviders;
import me.sshcrack.mc_talking.util.MiscUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static me.sshcrack.mc_talking.testing.CitizenPromptViewFixture.citizen;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Q5: complaint wording follows how long a problem has lasted, not only how bad it is. */
class ComplaintPromptTest {
    @BeforeAll
    static void installDefaultProvider() {
        TestPromptProviders.installDefault();
    }

    private static String prompt(CitizenPromptViewFixture fixture) {
        return MiscUtil.withFirstPicks(() -> PromptRuntime.generateCitizenRoleplayPrompt(fixture.build()));
    }

    private static CitizenPromptViewFixture homelessFor(int days, int colonyAge) {
        return citizen().homeless().colonyAgeDays(colonyAge)
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.HOMELESSNESS, 0.0, days));
    }

    @Test
    void newHomelessCitizenInNewColonyOnlyMentionsItInPassing() {
        String prompt = prompt(homelessFor(0, 1));
        assertTrue(prompt.contains("You don't have a home yet"), prompt);
        assertTrue(prompt.contains("Would like a home of your own at some point"), prompt);
        assertFalse(prompt.contains("desperately"), prompt);
        assertFalse(prompt.contains("Very concerned about not having a home"), prompt);
    }

    @Test
    void longHomelessnessInAnEstablishedColonyBecomesADemand() {
        String prompt = prompt(homelessFor(12, 40));
        assertTrue(prompt.contains("You desperately need a home"), prompt);
        assertTrue(prompt.contains("Very concerned about not having a home"), prompt);
    }

    @Test
    void youngColonyKeepsEarlyHomelessnessMild() {
        String prompt = prompt(homelessFor(3, 3));
        assertTrue(prompt.contains("You don't have a home yet"), prompt);
    }

    @Test
    void unemploymentEscalatesToo() {
        String early = prompt(citizen().job(null, null).colonyAgeDays(40)
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.UNEMPLOYMENT, 0.2, 0)));
        String late = prompt(citizen().job(null, null).colonyAgeDays(40)
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.UNEMPLOYMENT, 0.2, 6)));
        assertTrue(early.contains("Hoping to be given a job soon"), early);
        assertTrue(late.contains("Really wishing for a job after so long without one"), late);
        assertTrue(late.contains("You've been without a job for a long while"), late);
    }

    private static CitizenPromptViewFixture unsafe(int colonyAge, boolean raided) {
        CitizenPromptViewFixture fixture = citizen().colonyAgeDays(colonyAge)
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.SECURITY, 0.1, 0));
        ColonyPromptView c = fixture.build().colony();
        return fixture.colony(new ColonyPromptView(c.id(), c.name(), false, c.foundingPlayer(), colonyAge,
                raided ? 1_000L : null, 0, c.currentGameTimeTicks(), c.recentEvents(), c.connections(),
                c.milestone(), c.environment()));
    }

    @Test
    void youngColonyWithoutARaidFearsNothing() {
        String prompt = prompt(unsafe(1, false));
        assertFalse(prompt.contains("guards"), prompt);
    }

    @Test
    void terrorNeedsARealRaid() {
        String calm = prompt(unsafe(30, false));
        String raided = prompt(unsafe(30, true));
        assertTrue(calm.contains("You wish there were more guards patrolling the colony"), calm);
        assertFalse(calm.contains("terrified"), calm);
        assertTrue(raided.contains("You feel terrified"), raided);
    }

    @Test
    void youngColonyIsLongJoblessButStillHopeful() {
        String prompt = prompt(citizen().job(null, null).colonyAgeDays(3)
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.UNEMPLOYMENT, 0.2, 2)));
        assertTrue(prompt.contains("Hoping to be given a job soon"), prompt);
        assertFalse(prompt.contains("Concerned about not having a job"), prompt);
    }

    @Test
    void youngColonyIsHopefulAboutItsFounding() {
        assertTrue(prompt(citizen().colonyAgeDays(2)).contains("The colony was only just founded. You're hopeful"));
        assertTrue(prompt(citizen().colonyAgeDays(7)).contains("The colony is still young. You're mostly patient"));
        assertFalse(prompt(citizen().colonyAgeDays(12)).contains("only just founded"));
        assertFalse(prompt(citizen().colonyAgeDays(12)).contains("still young"));
    }
}
