package me.sshcrack.mc_talking.conversations.construction;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import me.sshcrack.mc_talking.testing.CitizenPromptViewFixture;
import me.sshcrack.mc_talking.testing.TestPromptProviders;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionPromptsTest {
    private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000a11a");

    private static ConstructionSite residence(int percent, String stage, List<String> missing, List<String> onTheWay) {
        return new ConstructionSite("Residence", ConstructionSite.Kind.UPGRADE, 2, "Anna", ANNA, percent, stage, missing, onTheWay);
    }

    @BeforeAll
    static void installDefaultProvider() {
        TestPromptProviders.installDefault();
    }

    @Test
    void progressReadsLikeTheBuildersWindow() {
        assertEquals("just getting started", ConstructionPrompts.progress(-1));
        assertEquals("only just started", ConstructionPrompts.progress(4));
        assertEquals("about a quarter done", ConstructionPrompts.progress(30));
        assertEquals("about half done", ConstructionPrompts.progress(50));
        assertEquals("well along", ConstructionPrompts.progress(80));
        assertEquals("nearly finished", ConstructionPrompts.progress(95));
    }

    @Test
    void aSiteSaysWhoBuildsItHowFarAlongAndWhatIsMissing() {
        assertEquals("The Residence (upgrade to level 2), built by Anna: about half done, the walls are going up. "
                        + "Anna is waiting for materials nobody in the colony has: 32x Oak Planks, 12x Glass, 4x Door and more. "
                        + "On their way: 8x Torch.",
                ConstructionPrompts.line(residence(52, "BUILD_SOLID",
                        List.of("32x Oak Planks", "12x Glass", "4x Door", "2x Bed"), List.of("8x Torch")), null));
        assertEquals("The Residence (upgrade to level 2), built by Anna: nearly finished, the finishing touches are going on. "
                        + "Anna's materials are on their way: 8x Torch.",
                ConstructionPrompts.line(residence(93, "DECORATE", List.of(), List.of("8x Torch")), null));
    }

    @Test
    void theBuilderHearsAboutTheirOwnJob() {
        assertEquals("The Residence (upgrade to level 2), your job: about a quarter done, the site is being cleared. "
                        + "You are waiting for materials nobody in the colony has: 10x Cobblestone.",
                ConstructionPrompts.line(residence(20, "CLEAR", List.of("10x Cobblestone"), List.of()), ANNA));
    }

    @Test
    void unclaimedJobsWaitForABuilder() {
        assertEquals("The Hospital (new): no builder has taken it on yet.", ConstructionPrompts.line(
                new ConstructionSite("Hospital", ConstructionSite.Kind.NEW, 1, null, null, -1, null, List.of(), List.of()), null));
        assertEquals("", ConstructionPrompts.section(List.of(), null));
    }

    @Test
    void everyCitizenSeesTheSectionInTheirPrompt() {
        CitizenPromptView view = CitizenPromptViewFixture.withConstruction(CitizenPromptViewFixture.citizen().build(),
                new ConstructionContext(List.of(residence(52, "BUILD_SOLID", List.of("32x Oak Planks"), List.of()))));
        String prompt = PromptRuntime.generateCitizenRoleplayPrompt(view);
        assertTrue(prompt.contains("## CONSTRUCTION IN THE COLONY\n- The Residence (upgrade to level 2), built by Anna: about half done"),
                prompt);
        assertFalse(PromptRuntime.generateCitizenRoleplayPrompt(CitizenPromptViewFixture.citizen().build())
                .contains("CONSTRUCTION IN THE COLONY"));
    }
}
