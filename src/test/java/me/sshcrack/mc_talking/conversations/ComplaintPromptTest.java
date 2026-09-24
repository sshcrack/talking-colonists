package me.sshcrack.mc_talking.conversations;

import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
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
        String prompt = prompt(homelessFor(8, 40));
        assertTrue(prompt.contains("You desperately need a home"), prompt);
        assertTrue(prompt.contains("Very concerned about not having a home"), prompt);
    }

    @Test
    void youngColonyKeepsLongHomelessnessMild() {
        String prompt = prompt(homelessFor(5, 5));
        assertTrue(prompt.contains("You don't have a home yet"), prompt);
    }

    @Test
    void unemploymentEscalatesToo() {
        String early = prompt(citizen().job(null, null)
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.UNEMPLOYMENT, 0.2, 0)));
        String late = prompt(citizen().job(null, null)
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.UNEMPLOYMENT, 0.2, 6)));
        assertTrue(early.contains("Hoping to be given a job soon"), early);
        assertTrue(late.contains("Fed up with having no job for so long"), late);
        assertTrue(late.contains("You've been without a job for so long"), late);
    }
}
