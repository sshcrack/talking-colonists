package me.sshcrack.mc_talking.conversations.complaints;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.manager.prompt.HappinessPromptSection;
import me.sshcrack.mc_talking.testing.CitizenPromptViewFixture;
import me.sshcrack.mc_talking.util.ComplaintRamp;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintNotesInPromptTest {
    private static String render(CitizenPromptView view) {
        StringBuilder prompt = new StringBuilder();
        HappinessPromptSection.append(view, prompt, ComplaintRamp.Settings.DEFAULTS);
        return prompt.toString();
    }

    @Test
    void theNoteFollowsTheProblemItBelongsTo() {
        CitizenPromptView view = CitizenPromptViewFixture.citizen()
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.HOMELESSNESS, 0.5, 9))
                .build();
        var note = new ComplaintHistory.Note(ComplaintTopic.HOUSING, ComplaintStage.FRUSTRATED, 3, 0, 2, false, false, false);
        String prompt = render(CitizenPromptViewFixture.withComplaints(view, new ComplaintContext(Map.of(ComplaintTopic.HOUSING, note),
                List.of(new ComplaintHistory.Residue(ComplaintTopic.WORK, UUID.randomUUID(), 2, 8)))));

        String[] lines = prompt.split("\n");
        int problem = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("You've raised this with them 3 times since day 2")) problem = i;
        }
        assertTrue(problem > 0 && lines[problem - 1].startsWith("- "), "note right under the housing line:\n" + prompt);
        assertTrue(prompt.contains("having no job was finally fixed on day 8"), prompt);
        assertTrue(prompt.contains("raise_concern"), prompt);
    }

    @Test
    void noToolHintWhenTheToolIsDisabled() {
        CitizenPromptView view = CitizenPromptViewFixture.citizen()
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.HOMELESSNESS, 0.5, 9))
                .build();
        var note = new ComplaintHistory.Note(ComplaintTopic.HOUSING, ComplaintStage.FIRST_MENTION, 0, 0, 9, false, false, false);
        StringBuilder prompt = new StringBuilder();
        HappinessPromptSection.append(CitizenPromptViewFixture.withComplaints(view,
                new ComplaintContext(Map.of(ComplaintTopic.HOUSING, note), List.of())), prompt, ComplaintRamp.Settings.DEFAULTS, false);
        assertTrue(prompt.toString().contains("You haven't raised this with them yet"), prompt.toString());
        assertFalse(prompt.toString().contains("raise_concern"), "the model would say the call out loud");
    }

    @Test
    void viewsWithoutHistoryAreUnchanged() {
        CitizenPromptView view = CitizenPromptViewFixture.citizen().build();
        String prompt = render(view);
        assertFalse(prompt.contains("raise_concern") || prompt.contains("raised this"), prompt);
    }
}
