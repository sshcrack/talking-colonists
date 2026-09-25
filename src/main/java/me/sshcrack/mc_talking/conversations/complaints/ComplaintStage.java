package me.sshcrack.mc_talking.conversations.complaints;

/**
 * How a citizen voices a problem to a player, from how often they raised it and how the player
 * reacted, not from how long it has lasted (that is {@code ComplaintRamp}'s job).
 */
public enum ComplaintStage {
    /** Never raised with this player: honest and constructive. */
    FIRST_MENTION,
    /** Raised before: a pointed reminder ("like I said..."). */
    REMINDER,
    /** Raised again and again without a fix: annoyed, blunt or sarcastic. */
    FRUSTRATED,
    /** Gave up asking: curt or bitter, complains to others instead. */
    RESIGNED;

    ComplaintStage atMost(ComplaintStage cap) {
        return ordinal() <= cap.ordinal() ? this : cap;
    }
}
