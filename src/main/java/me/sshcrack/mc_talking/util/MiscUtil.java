package me.sshcrack.mc_talking.util;

public class MiscUtil {
    public static String describeTime(long dayTime) {
        if (dayTime < 1000) return "early morning (sunrise)";
        if (dayTime < 6000) return "morning";
        if (dayTime < 9000) return "midday";
        if (dayTime < 12000) return "afternoon";
        if (dayTime < 13000) return "sunset";
        if (dayTime < 18000) return "night";
        if (dayTime < 22000) return "late night";
        return "pre-dawn";
    }
}
