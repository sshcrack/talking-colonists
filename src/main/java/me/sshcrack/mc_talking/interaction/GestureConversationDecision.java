package me.sshcrack.mc_talking.interaction;

/**
 * Pure decision logic for starting or ending a conversation via a non-device entry point:
 * the sneak+attack gesture, or the optional "talk to citizen" keybind.
 *
 * <p>Deliberately free of Minecraft/MineColonies types so the decision table can be unit
 * tested directly. Callers ({@code TalkToCitizenHandler}) compute the booleans from the
 * real player/citizen state and act on the returned {@link Outcome}.</p>
 */
public final class GestureConversationDecision {

    public enum Outcome {
        /** The feature is disabled server-side; the caller must not intercept vanilla behavior. */
        FEATURE_DISABLED,
        /** The player is already talking to this exact citizen; end that conversation instead. */
        END_CONVERSATION,
        /** The citizen is further away than the allowed range. */
        TOO_FAR,
        /** Visitors cannot hold conversations. */
        VISITOR_BLOCKED,
        /** The voice chat provider is not ready. */
        VOICECHAT_UNAVAILABLE,
        /** The player's own voice chat connection is disabled. */
        VOICECHAT_DISABLED,
        /** The citizen cannot currently speak (busy/sleeping/cooldown/addon veto). */
        CANNOT_SPEAK,
        /** All checks passed; the caller should attempt to start the conversation. */
        ATTEMPT_START
    }

    private GestureConversationDecision() {
    }

    /**
     * @param featureEnabled              whether {@code enableTalkWithoutDevice} is on
     * @param alreadyTalkingToThisCitizen whether the player currently owns a direct conversation with this exact citizen
     * @param withinRange                 whether the citizen is within the configured max conversation distance
     * @param isVisitor                   whether the target is a MineColonies visitor rather than a colonist
     * @param voicechatApiReady           whether the Simple Voice Chat server API has finished initializing
     * @param voicechatDisabledForPlayer  whether the player's own voice chat connection is currently disabled
     * @param citizenCanSpeak             the result of {@code ConversationManager.canCitizenSpeak(citizen, true)}
     */
    public static Outcome decide(
            boolean featureEnabled,
            boolean alreadyTalkingToThisCitizen,
            boolean withinRange,
            boolean isVisitor,
            boolean voicechatApiReady,
            boolean voicechatDisabledForPlayer,
            boolean citizenCanSpeak
    ) {
        if (!featureEnabled) {
            return Outcome.FEATURE_DISABLED;
        }
        if (alreadyTalkingToThisCitizen) {
            return Outcome.END_CONVERSATION;
        }
        if (!withinRange) {
            return Outcome.TOO_FAR;
        }
        if (isVisitor) {
            return Outcome.VISITOR_BLOCKED;
        }
        if (!voicechatApiReady) {
            return Outcome.VOICECHAT_UNAVAILABLE;
        }
        if (voicechatDisabledForPlayer) {
            return Outcome.VOICECHAT_DISABLED;
        }
        if (!citizenCanSpeak) {
            return Outcome.CANNOT_SPEAK;
        }
        return Outcome.ATTEMPT_START;
    }
}
