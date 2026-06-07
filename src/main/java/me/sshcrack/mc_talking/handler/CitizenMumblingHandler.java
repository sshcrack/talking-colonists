package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles citizen mumbling (idle chat) and casual greetings toward nearby players.
 */
public class CitizenMumblingHandler {
    private CitizenMumblingHandler() {
    }

    public static void checkForMumblingCitizens(List<AbstractEntityCitizen> citizens,
                                                  Set<UUID> mumbledThisInterval) {
        for (AbstractEntityCitizen citizen : citizens) {
            if (!ConversationManager.canCitizenSpeak(citizen))
                continue;
            if (!mumbledThisInterval.add(citizen.getUUID()))
                continue;
            if (Math.random() < McTalkingConfig.INSTANCE.instance().mumblingChance) {
                ConversationManager.startMumbling(citizen);
                break;
            }
        }
    }
}
