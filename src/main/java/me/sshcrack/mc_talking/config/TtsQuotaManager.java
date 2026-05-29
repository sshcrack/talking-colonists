package me.sshcrack.mc_talking.config;

public class TtsQuotaManager {
    private static boolean ttsFailed = false;

    public static boolean isTtsFailed() {
        return ttsFailed;
    }

    public static void setTtsFailed(boolean failed) {
        ttsFailed = failed;
    }
}
