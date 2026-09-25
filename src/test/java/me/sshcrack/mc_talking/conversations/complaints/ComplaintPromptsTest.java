package me.sshcrack.mc_talking.conversations.complaints;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintPromptsTest {
    private static ComplaintHistory.Note note(ComplaintStage stage, int raised, int answered, boolean today, boolean promise,
                                              boolean progress) {
        return new ComplaintHistory.Note(ComplaintTopic.HOUSING, stage, raised, answered, 3, today, promise, progress);
    }

    @Test
    void eachStageReadsDifferently() {
        assertTrue(ComplaintPrompts.note(note(ComplaintStage.FIRST_MENTION, 0, 0, false, false, false)).contains("constructive"));
        assertTrue(ComplaintPrompts.note(note(ComplaintStage.REMINDER, 1, 0, false, false, false))
                .contains("once since day 3, but they didn't respond"));
        assertTrue(ComplaintPrompts.note(note(ComplaintStage.REMINDER, 2, 2, false, false, true)).contains("at least it's happening"));
        String frustrated = ComplaintPrompts.note(note(ComplaintStage.FRUSTRATED, 3, 3, false, true, false));
        assertTrue(frustrated.contains("3 times since day 3") && frustrated.contains("even though they heard you out")
                && frustrated.contains("sarcastic"), frustrated);
        assertTrue(ComplaintPrompts.note(note(ComplaintStage.RESIGNED, 4, 0, false, false, false)).contains("given up asking"));
    }

    @Test
    void aProblemRaisedTodayIsNotRepeated() {
        assertTrue(ComplaintPrompts.note(note(ComplaintStage.FRUSTRATED, 3, 0, true, false, false))
                .contains("already raised this with them today"));
    }

    @Test
    void aFixAfterFrustrationStillStings() {
        String residue = ComplaintPrompts.residue(new ComplaintHistory.Residue(ComplaintTopic.HOUSING, UUID.randomUUID(), 4, 9));
        assertTrue(residue.contains("your home was finally fixed on day 9") && residue.contains("4 times"), residue);
    }
}
