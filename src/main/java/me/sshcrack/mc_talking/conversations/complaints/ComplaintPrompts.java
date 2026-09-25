package me.sshcrack.mc_talking.conversations.complaints;

/** How a citizen's complaint history reads in their prompt. Pure text, no game state. */
public final class ComplaintPrompts {
    public static final String TOOL_NAME = "raise_concern";
    /** Tells the citizen to report raising a problem, so the history stays right in any language. */
    public static final String TOOL_HINT = "- When you bring up one of these problems with the player, also call raise_concern with its "
            + "topic (housing, work, health, supplies, food or security). Never mention that you do.\n";

    private ComplaintPrompts() {
    }

    /** The note after a problem's line in the current state, e.g. "  (You've raised this with them 3 times ...)". */
    public static String note(ComplaintHistory.Note note) {
        if (note.raisedToday()) {
            return "  (You already raised this with them today. Don't bring it up again unless they ask or it gets worse.)\n";
        }
        String since = times(note.raised()) + " since day " + note.firstDay();
        return switch (note.stage()) {
            case FIRST_MENTION -> "  (You haven't raised this with them yet. If you bring it up, be honest and constructive: "
                    + "what bothers you and what would help.)\n";
            case REMINDER -> "  (You've raised this with them " + since
                    + (note.answered() > 0 ? ", and they listened" : ", but they didn't respond")
                    + (note.progressVisible()
                    ? ". It's being worked on now, so you're patient: at least it's happening.)\n"
                    : ". If you bring it up, remind them pointedly, like \"as I said before...\".)\n");
            case FRUSTRATED -> "  (You've raised this with them " + since + " and it's still not fixed"
                    + (note.brokenPromise() ? ", even though they heard you out" : ", and they barely responded")
                    + ". You're fed up: it's fine to be openly annoyed, blunt or sarcastic about it, in your own way.)\n";
            case RESIGNED -> "  (You've raised this with them " + since + " and have given up asking. Don't ask again; "
                    + "if it comes up, be curt, cold or bitter. You'd rather grumble about it to others.)\n";
        };
    }

    /** A problem that was fixed while the citizen was frustrated about it. */
    public static String residue(ComplaintHistory.Residue residue) {
        return "- Your problem with " + residue.topic().description() + " was finally fixed on day " + residue.fixedDay()
                + ", after you had raised it " + times(residue.raised())
                + ". You're relieved, but still a little sore about how long it took.\n";
    }

    /** A short addition for the urgent-contact prompt, which is one instruction to walk up and speak. */
    public static String urgent(ComplaintHistory.Note note) {
        return switch (note.stage()) {
            case FIRST_MENTION -> " It's the first time you tell them: be honest and constructive about what would help.";
            case REMINDER -> " You've told them about this " + times(note.raised()) + " already; remind them pointedly.";
            case FRUSTRATED -> " You've told them about this " + times(note.raised())
                    + " already and nothing changed: you're fed up, so be openly annoyed, blunt or sarcastic.";
            case RESIGNED -> " You've told them " + times(note.raised())
                    + " already and don't expect anything anymore: be curt and bitter.";
        };
    }

    static String times(int count) {
        return count == 1 ? "once" : count == 2 ? "twice" : count + " times";
    }
}
