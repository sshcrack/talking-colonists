package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.CitizenConversation;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles citizen-to-citizen random conversations triggered when two
 * unrelated citizens are near each other (and near a player).
 */
public class RandomConversationHandler {
    private RandomConversationHandler() {
    }

    public static void checkForRandomConversations(MinecraftServer server) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty())
            return;

        double range = McTalkingConfig.INSTANCE.instance().citizenInteractionRange * 2;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var nearbyBox = player.getBoundingBox().inflate(range);
            var citizens = player.serverLevel().getEntitiesOfClass(AbstractEntityCitizen.class, nearbyBox);

            boolean anyBusy = citizens.stream()
                    .anyMatch(ConversationManager::isCitizenBusy);
            if (anyBusy)
                continue;

            for (AbstractEntityCitizen citizen : citizens) {
                if (!ConversationManager.canCitizenSpeak(citizen))
                    continue;

                if (Math.random() >= McTalkingConfig.INSTANCE.instance().randomConversationChance)
                    continue;

                List<AbstractEntityCitizen> partners = new ArrayList<>();
                for (AbstractEntityCitizen candidate : citizens) {
                    if (candidate == citizen)
                        continue;
                    if (!ConversationManager.canCitizenSpeak(candidate))
                        continue;
                    partners.add(candidate);
                }

                if (partners.isEmpty())
                    continue;

                AbstractEntityCitizen partner = partners.get((int) (Math.random() * partners.size()));

                McTalking.LOGGER.info("[RandomConv] Starting conversation between {} and {}",
                        citizen.getCitizenData().getName(), partner.getCitizenData().getName());

                var conversation = new CitizenConversation(server, List.of(citizen, partner));
                conversation.setOnStateChanged(newState -> {
                    AiStatus status = switch (newState) {
                        case GENERATING -> AiStatus.THINKING;
                        case PLAYING_AUDIO -> AiStatus.IN_CONVERSATION;
                        case ENDED -> AiStatus.NONE;
                    };
                    AiStatusHelper.setAiStatusSynced(citizen, status);
                    AiStatusHelper.setAiStatusSynced(partner, status);
                });
                conversation.performConversation();

                return;
            }
        }
    }
}
