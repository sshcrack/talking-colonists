package me.sshcrack.mc_talking.network;

/// Don't forget to also update `en_us.json`!!
public enum AiStatus {
    ERROR,
    THINKING,
    QUOTA_EXCEEDED,
    TALKING,
    LISTENING,
    NONE;
    
    public static AiStatus fromId(int id) {
        if (id < 0 || id >= values().length) {
            return NONE;
        }
        return values()[id];
    }
}
