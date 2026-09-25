package me.sshcrack.mc_talking.conversations.complaints;

import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplaintHistoryTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final ComplaintTopic HOME = ComplaintTopic.HOUSING;

    private static ComplaintStage stage(ComplaintHistory history, int day) {
        return history.stage(HOME, PLAYER, day, ComplaintPace.NORMAL);
    }

    @Test
    void theFirstMentionIsConstructiveAndEachPlayerHasTheirOwnHistory() {
        ComplaintHistory history = new ComplaintHistory();
        assertEquals(ComplaintStage.FIRST_MENTION, stage(history, 1));
        history.recordRaised(HOME, PLAYER, 1);
        assertEquals(ComplaintStage.REMINDER, stage(history, 2));
        assertEquals(ComplaintStage.FIRST_MENTION, history.stage(HOME, OTHER, 2, ComplaintPace.NORMAL));
    }

    @Test
    void aProblemCountsOnceADay() {
        ComplaintHistory history = new ComplaintHistory();
        assertTrue(history.recordRaised(HOME, PLAYER, 1));
        assertFalse(history.recordRaised(HOME, PLAYER, 1), "a walk-up and the chat after it are one complaint");
        assertEquals(1, history.entry(HOME, PLAYER).raised());
        assertTrue(history.note(HOME, PLAYER, 1, ComplaintPace.NORMAL, false).raisedToday());
    }

    @Test
    void beingIgnoredEscalatesFasterThanBeingHeard() {
        ComplaintHistory ignored = new ComplaintHistory();
        ignored.recordRaised(HOME, PLAYER, 1);
        ignored.recordRaised(HOME, PLAYER, 2);
        assertEquals(ComplaintStage.FRUSTRATED, stage(ignored, 3), "twice ignored");

        ComplaintHistory heard = new ComplaintHistory();
        heard.recordRaised(HOME, PLAYER, 1);
        heard.recordAnswered(PLAYER, 1);
        heard.recordRaised(HOME, PLAYER, 2);
        heard.recordAnswered(PLAYER, 2);
        assertEquals(ComplaintStage.REMINDER, stage(heard, 3), "twice heard, still only a reminder");
        heard.recordRaised(HOME, PLAYER, 3);
        heard.recordAnswered(PLAYER, 3);
        assertEquals(ComplaintStage.FRUSTRATED, stage(heard, 4), "three times heard and never fixed");
        assertTrue(heard.note(HOME, PLAYER, 4, ComplaintPace.NORMAL, false).brokenPromise());
    }

    @Test
    void theyGiveUpAfterBeingIgnoredOftenEnough() {
        ComplaintHistory history = new ComplaintHistory();
        for (int day = 1; day <= 3; day++) history.recordRaised(HOME, PLAYER, day);
        assertEquals(ComplaintStage.RESIGNED, stage(history, 4));
    }

    @Test
    void aReplyBuysADayOfGrace() {
        ComplaintHistory history = new ComplaintHistory();
        for (int day = 1; day <= 2; day++) history.recordRaised(HOME, PLAYER, day);
        history.recordRaised(HOME, PLAYER, 3);
        history.recordAnswered(PLAYER, 3);
        assertEquals(ComplaintStage.REMINDER, stage(history, 3), "they just heard it out");
        assertEquals(ComplaintStage.FRUSTRATED, stage(history, 4), "a day later, still nothing");
    }

    @Test
    void visibleProgressKeepsThemPatient() {
        ComplaintHistory history = new ComplaintHistory();
        for (int day = 1; day <= 3; day++) history.recordRaised(HOME, PLAYER, day);
        assertEquals(ComplaintStage.REMINDER, history.note(HOME, PLAYER, 4, ComplaintPace.NORMAL, true).stage());
    }

    @Test
    void personalitySetsThePace() {
        ComplaintHistory history = new ComplaintHistory();
        history.recordRaised(HOME, PLAYER, 1);
        history.recordAnswered(PLAYER, 1);
        history.recordRaised(HOME, PLAYER, 2);
        assertEquals(ComplaintStage.FRUSTRATED, history.stage(HOME, PLAYER, 3, ComplaintPace.QUICK));
        assertEquals(ComplaintStage.REMINDER, history.stage(HOME, PLAYER, 3, ComplaintPace.PATIENT));
        history.recordRaised(HOME, PLAYER, 3);
        assertEquals(ComplaintStage.RESIGNED, history.stage(HOME, PLAYER, 4, ComplaintPace.WITHDRAWN),
                "timid citizens go quiet instead of angry");
        assertEquals(ComplaintPace.QUICK, ComplaintPace.of("GRUMP"));
        assertEquals(ComplaintPace.WITHDRAWN, ComplaintPace.of("TIMID"));
        assertEquals(ComplaintPace.NORMAL, ComplaintPace.of(null));
    }

    @Test
    void aFixClearsTheHistoryAndAFrustratingOneLeavesAResidue() {
        ComplaintHistory history = new ComplaintHistory();
        for (int day = 1; day <= 2; day++) history.recordRaised(HOME, PLAYER, day);
        history.recordRaised(ComplaintTopic.WORK, PLAYER, 2);
        history.resolve(Set.of(ComplaintTopic.WORK), 5, ComplaintPace.NORMAL);

        assertEquals(ComplaintStage.FIRST_MENTION, stage(history, 5));
        assertEquals(List.of(new ComplaintHistory.Residue(HOME, PLAYER, 2, 5)), history.residues(PLAYER));
        assertEquals(ComplaintStage.REMINDER, history.stage(ComplaintTopic.WORK, PLAYER, 5, ComplaintPace.NORMAL));

        history.resolve(Set.of(ComplaintTopic.WORK), 5 + ComplaintHistory.RESIDUE_DAYS + 1, ComplaintPace.NORMAL);
        assertTrue(history.residues(PLAYER).isEmpty(), "the soreness fades");
    }

    @Test
    void theHistorySurvivesAReload() {
        CitizenMemories memories = new CitizenMemories();
        memories.complaints().recordRaised(HOME, PLAYER, 1);
        memories.complaints().recordAnswered(PLAYER, 1);
        memories.complaints().recordRaised(ComplaintTopic.FOOD, OTHER, 2);
        memories.complaints().recordRaised(ComplaintTopic.WORK, PLAYER, 2);
        memories.complaints().recordRaised(ComplaintTopic.WORK, PLAYER, 3);
        memories.complaints().resolve(Set.of(HOME, ComplaintTopic.FOOD), 4, ComplaintPace.NORMAL);

        CitizenMemories reloaded = new CitizenMemories();
        reloaded.deserializeNbt(memories.serializeNbt());
        assertEquals(memories.complaints().entries(), reloaded.complaints().entries());
        assertEquals(memories.complaints().allResidues(), reloaded.complaints().allResidues());
        assertEquals(1, reloaded.complaints().residues(PLAYER).size());
    }
}
