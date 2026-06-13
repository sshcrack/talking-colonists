package me.sshcrack.mc_talking.api.prompt.view;

public enum FrustrationTier {
    NEUTRAL(0),
    MILDLY_ANNOYED(1),
    CONCERNED(2),
    AGITATED(3),
    FURIOUS(4);

    private final int level;

    FrustrationTier(int level) { this.level = level; }

    public int getLevel() { return level; }

    public static FrustrationTier forDuration(long ticks, long[] thresholds) {
        if (ticks >= thresholds[3]) return FURIOUS;
        if (ticks >= thresholds[2]) return AGITATED;
        if (ticks >= thresholds[1]) return CONCERNED;
        if (ticks >= thresholds[0]) return MILDLY_ANNOYED;
        return NEUTRAL;
    }
}
