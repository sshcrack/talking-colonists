package me.sshcrack.mc_talking.manager.prompt;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.connections.ColonyConnection;
import com.minecolonies.api.colony.connections.DiplomacyStatus;
import com.minecolonies.api.colony.connections.IColonyConnectionManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.view.ColonyPromptView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import me.sshcrack.mc_talking.util.ColonyStatsHelper;
import me.sshcrack.mc_talking.util.MiscUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds the colony part of the prompt context (moved from CitizenPromptViewFactory). */
public final class ColonyPromptViewFactory {
    private ColonyPromptViewFactory() {
    }

    /**
     * Colony-level prompt context. {@code level} supplies time of day, weather and difficulty; the
     * citizen path passes its entity's level, and without one those fields stay unknown.
     */
    public static ColonyPromptView createColonyView(IColony colony, @Nullable Level level) {
        var envInfo = extractEnvironmentInfo(level);
        long lastRaidEndTime = ColonyEventBuffer.getLastRaidEndTime(colony);
        List<String> colonyConnections = extractColonyConnections(colony);
        return new ColonyPromptView(
                colony.getID(),
                colony.getName(),
                envInfo.peaceful(),
                colony.getPermissions().getOwnerName(),
                colony.getDay(),
                lastRaidEndTime != Long.MAX_VALUE ? lastRaidEndTime : null,
                ColonyEventBuffer.getLostCitizens(colony),
                colony.getWorld() != null ? colony.getWorld().getGameTime() : 0,
                extractRecentEvents(colony),
                colonyConnections == null ? List.of() : colonyConnections,
                ColonyStatsHelper.getColonyMilestoneText(colony),
                envInfo.description()
        );
    }

    private static EnvironmentInfo extractEnvironmentInfo(@Nullable Level level) {
        if (level == null) {
            return new EnvironmentInfo(null, false);
        }
        long dayTime = level.getDayTime() % 24000L;
        String description = "It is " + MiscUtil.describeTime(dayTime) + " and " + describeWeather(level) + ".";
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
        return new EnvironmentInfo(description, peaceful);
    }

    @Nullable
    private static List<String> extractColonyConnections(IColony colony) {
        if (!McTalkingConfig.INSTANCE.instance().enableColonyDiplomacy) {
            return null;
        }
        try {
            IColonyConnectionManager connManager = colony.getConnectionManager();
            if (connManager == null) {
                return null;
            }
            List<String> connections = new ArrayList<>();
            TreeMap<Integer, ColonyConnection> direct = connManager.getDirectlyConnectedColonies();
            if (direct != null) {
                for (Map.Entry<Integer, ColonyConnection> entry : direct.entrySet()) {
                    try {
                        int targetId = entry.getKey();
                        ColonyConnection conn = entry.getValue();
                        String connName = conn.name != null ? conn.name : "Colony #" + targetId;
                        DiplomacyStatus status = connManager.getColonyDiplomacyStatus(targetId);
                        connections.add(connName + " (" + (status != null ? status.name() : "unknown") + ")");
                    } catch (Exception e) {
                        McTalking.LOGGER.warn("Failed to process direct colony connection {}", entry.getKey(), e);
                    }
                }
            }
            TreeMap<Integer, ColonyConnection> indirect = connManager.getIndirectlyConnectedColonies();
            if (indirect != null) {
                for (Map.Entry<Integer, ColonyConnection> entry : indirect.entrySet()) {
                    try {
                        int targetId = entry.getKey();
                        ColonyConnection conn = entry.getValue();
                        String connName = conn.name != null ? conn.name : "Colony #" + targetId;
                        DiplomacyStatus status = connManager.getColonyDiplomacyStatus(targetId);
                        connections.add(connName + " (" + (status != null ? status.name() : "unknown") + ")");
                    } catch (Exception e) {
                        McTalking.LOGGER.warn("Failed to process indirect colony connection {}", entry.getKey(), e);
                    }
                }
            }
            return connections.isEmpty() ? null : connections;
        } catch (Exception e) {
            McTalking.LOGGER.warn("Failed to extract colony connections", e);
            return null;
        }
    }

    private static List<String> extractRecentEvents(IColony colony) {
        int eventWindow = McTalkingConfig.INSTANCE.instance().colonyEventWindowSeconds;
        if (eventWindow <= 0) {
            return List.of();
        }
        return ColonyEventBuffer.getRecentEvents(colony, eventWindow).stream()
                .map(ColonyEventBuffer.ColonyEvent::description)
                .toList();
    }

    private record EnvironmentInfo(@Nullable String description, boolean peaceful) {}

    private static String describeWeather(Level level) {
        if (level.isThundering()) return "thundering";
        if (level.isRaining()) return "rainy";
        return "clear";
    }
}
