package me.sshcrack.mc_talking.internal.session;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * The per-player ambient speech budget: how many unprompted lines each player hears in a rolling
 * window ({@code ambientSpeechBudget*} config). Checking is free; spending happens once per line,
 * right before it is certain to start. The counting itself is {@link AmbientSpeechBudgetRegistry}.
 */
public final class AmbientSpeechBudget {
    private static final AmbientSpeechBudgetRegistry registry = new AmbientSpeechBudgetRegistry(System::currentTimeMillis);

    private AmbientSpeechBudget() {
    }

    /** Player-started, urgent, and controlled/meeting sessions are exempt from the budget. */
    public static boolean appliesTo(ConversationKind kind) {
        return kind != ConversationKind.PLAYER
                && kind != ConversationKind.URGENT_CONTACT
                && kind != ConversationKind.CONTROLLED;
    }

    /** Read-only: would every player who would currently hear {@code citizen} have budget room? */
    public static boolean hasCapacity(AbstractEntityCitizen citizen) {
        var config = McTalkingConfig.INSTANCE.instance();
        if (!config.enableAmbientSpeechBudget) return true;
        List<UUID> hearers = nearbyHearingPlayerIds(citizen, config.ambientSpeechBudgetHearingRange);
        long windowMillis = config.ambientSpeechBudgetWindowSeconds * 1000L;
        return registry.hasCapacity(hearers, config.ambientSpeechBudgetMaxLines, windowMillis);
    }

    /**
     * Spends one unit of budget against every player who would hear {@code citizen} (and, for a
     * two-citizen conversation, {@code other}) speak right now. Returns {@code true} (having recorded
     * the spend) unless any such player is already at their per-window limit, in which case nothing
     * is recorded and the whole line should be skipped — see
     * {@link AmbientSpeechBudgetRegistry#tryConsume} for why a line is not partially charged
     * against listeners who still have room.
     *
     * <p>This is the single point that actually enforces the budget. Call it exactly once per
     * ambient line/conversation-start attempt, immediately before it is guaranteed to be spoken or
     * played — never speculatively while merely scanning candidates.</p>
     */
    public static boolean trySpend(AbstractEntityCitizen citizen, @Nullable AbstractEntityCitizen other) {
        var config = McTalkingConfig.INSTANCE.instance();
        if (!config.enableAmbientSpeechBudget) return true;

        LinkedHashSet<UUID> hearers = new LinkedHashSet<>(
                nearbyHearingPlayerIds(citizen, config.ambientSpeechBudgetHearingRange));
        if (other != null) {
            hearers.addAll(nearbyHearingPlayerIds(other, config.ambientSpeechBudgetHearingRange));
        }
        long windowMillis = config.ambientSpeechBudgetWindowSeconds * 1000L;
        return registry.tryConsume(hearers, config.ambientSpeechBudgetMaxLines, windowMillis);
    }

    public static boolean trySpend(AbstractEntityCitizen citizen) {
        return trySpend(citizen, null);
    }

    public static void clear() {
        registry.clear();
    }

    /** Players in the same dimension as {@code citizen} within {@code range} blocks. */
    private static List<UUID> nearbyHearingPlayerIds(AbstractEntityCitizen citizen, double range) {
        MinecraftServer server = citizen.level().getServer();
        if (server == null) return List.of();
        double rangeSqr = range * range;
        List<UUID> result = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() == citizen.level() && player.distanceToSqr(citizen) <= rangeSqr) {
                result.add(player.getUUID());
            }
        }
        return result;
    }
}
