package me.sshcrack.mc_talking.interaction;

import org.junit.jupiter.api.Test;

import static me.sshcrack.mc_talking.interaction.GestureConversationDecision.Outcome;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GestureConversationDecisionTest {

    /** All checks pass -> the caller should attempt to start the conversation. */
    @Test
    void allowsStartWhenEverythingIsEligible() {
        assertEquals(Outcome.ATTEMPT_START, decide(true, false, true, false, true, false, true));
    }

    @Test
    void disabledFeatureShortCircuitsBeforeAnyOtherCheck() {
        // Every other input says "start", but the feature is off.
        assertEquals(Outcome.FEATURE_DISABLED, decide(false, false, true, false, true, false, true));
    }

    @Test
    void alreadyTalkingToThisCitizenEndsRegardlessOfOtherState() {
        // Visitor/out-of-range/etc. would normally block a start, but ending never needs them.
        assertEquals(Outcome.END_CONVERSATION, decide(true, true, false, true, false, true, false));
    }

    @Test
    void tooFarBlocksBeforeVisitorAndSpeechChecks() {
        assertEquals(Outcome.TOO_FAR, decide(true, false, false, true, true, false, true));
    }

    @Test
    void visitorsAreBlockedWhenInRange() {
        assertEquals(Outcome.VISITOR_BLOCKED, decide(true, false, true, true, true, false, true));
    }

    @Test
    void voicechatUnavailableBlocksStart() {
        assertEquals(Outcome.VOICECHAT_UNAVAILABLE, decide(true, false, true, false, false, false, true));
    }

    @Test
    void voicechatDisabledForPlayerBlocksStartEvenWhenApiReady() {
        assertEquals(Outcome.VOICECHAT_DISABLED, decide(true, false, true, false, true, true, true));
    }

    @Test
    void citizenThatCannotSpeakBlocksStart() {
        assertEquals(Outcome.CANNOT_SPEAK, decide(true, false, true, false, true, false, false));
    }

    private static Outcome decide(
            boolean featureEnabled,
            boolean alreadyTalkingToThisCitizen,
            boolean withinRange,
            boolean isVisitor,
            boolean voicechatApiReady,
            boolean voicechatDisabledForPlayer,
            boolean citizenCanSpeak
    ) {
        return GestureConversationDecision.decide(
                featureEnabled, alreadyTalkingToThisCitizen, withinRange, isVisitor,
                voicechatApiReady, voicechatDisabledForPlayer, citizenCanSpeak);
    }
}
