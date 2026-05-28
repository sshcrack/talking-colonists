package me.sshcrack.mc_talking.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;

public class CitizenHelper {
    private CitizenHelper() {
        /* This utility class should not be instantiated */
    }

    public static boolean isCitizenGuard(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        if (data == null)
            return false;

        var job = data.getJob();
        if (job == null)
            return false;

        return job.isGuard();
    }
}
