package me.sshcrack.mc_talking.conversations;

import me.sshcrack.mc_talking.api.prompt.view.VisitorPromptView;
import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import me.sshcrack.mc_talking.testing.TestPromptProviders;
import me.sshcrack.mc_talking.util.MiscUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static me.sshcrack.mc_talking.testing.CitizenPromptViewFixture.citizen;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A8: a visitor is introduced as a tavern guest, never as an unemployed or homeless colonist. */
class VisitorPromptTest {
    @BeforeAll
    static void installDefaultProvider() {
        TestPromptProviders.installDefault();
    }

    @Test
    void visitorIsATravellerWithRecruitCostAndNoColonistComplaints() {
        String prompt = MiscUtil.withFirstPicks(() -> PromptRuntime.generateCitizenRoleplayPrompt(
                citizen().visitor(new VisitorPromptView("3 x Diamond", 2)).build()));

        assertTrue(prompt.contains("**visitor**"), prompt);
        assertTrue(prompt.contains("a traveller staying at the tavern of Riverside"), prompt);
        assertTrue(prompt.contains("Staying for 2 days"), prompt);
        assertTrue(prompt.contains("recruit you for 3 x Diamond"), prompt);
        assertFalse(prompt.contains("unemployed"), prompt);
        assertFalse(prompt.contains("not having a job"), prompt);
        assertFalse(prompt.contains("homeless"), prompt);
        assertFalse(prompt.contains("not having a home"), prompt);
    }

    @Test
    void colonistsAreUnchanged() {
        String prompt = MiscUtil.withFirstPicks(() -> PromptRuntime.generateCitizenRoleplayPrompt(citizen().build()));
        assertFalse(prompt.contains("traveller staying at the tavern"), prompt);
    }
}
