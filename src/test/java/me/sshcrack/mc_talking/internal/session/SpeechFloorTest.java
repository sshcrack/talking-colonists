package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechFloorTest {
    private static final Object OVERWORLD = new Object();
    private static final Object NETHER = new Object();

    private static SpeechFloor.Voice at(Object level, double x) {
        return new SpeechFloor.Voice(UUID.randomUUID(), level, x, 64, 0);
    }

    @Test
    void speakerHeardByTheSameListenerBlocks() {
        var candidate = at(OVERWORLD, 0);
        var speaker = at(OVERWORLD, 10);
        var player = at(OVERWORLD, 5);
        assertTrue(SpeechFloor.wouldOverlap(candidate, List.of(speaker), List.of(player), Set.of(candidate.id()), 24));
    }

    @Test
    void nobodyToHearBothMeansNoOverlap() {
        var candidate = at(OVERWORLD, 0);
        var speaker = at(OVERWORLD, 60);
        var nearCandidate = at(OVERWORLD, 5);
        var nearSpeaker = at(OVERWORLD, 55);
        assertFalse(SpeechFloor.wouldOverlap(candidate, List.of(speaker), List.of(nearCandidate, nearSpeaker),
                Set.of(candidate.id()), 24));
        assertFalse(SpeechFloor.wouldOverlap(candidate, List.of(speaker), List.of(), Set.of(candidate.id()), 24));
    }

    @Test
    void ownGroupAndOtherLevelsDoNotBlock() {
        var candidate = at(OVERWORLD, 0);
        var teller = at(OVERWORLD, 2);
        var elsewhere = at(NETHER, 2);
        var player = at(OVERWORLD, 1);
        assertFalse(SpeechFloor.wouldOverlap(candidate, List.of(teller, elsewhere), List.of(player),
                Set.of(candidate.id(), teller.id()), 24));
    }

    @Test
    void zeroRadiusDisablesTheFloor() {
        var candidate = at(OVERWORLD, 0);
        assertFalse(SpeechFloor.wouldOverlap(candidate, List.of(at(OVERWORLD, 1)), List.of(at(OVERWORLD, 0)),
                Set.of(candidate.id()), 0));
    }
}
