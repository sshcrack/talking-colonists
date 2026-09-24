package me.sshcrack.mc_talking.broadcast;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.internal.audio.SpeechTimeline;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Makes news passing between citizens readable: when a rumor or colony news goes from one citizen
 * to another while a player is watching, both stop, turn to each other and show the conversation
 * bubble for a few seconds, and players close by get a subtitle ("Kayla whispers some gossip to
 * Mila"). Rumor content stays hidden, so the player has a reason to go and ask. Server thread only.
 */
public final class GossipMoments {
    public enum Kind {
        RUMOR, BROADCAST;

        /** The subtitle's translation key; rumors never say what was whispered. */
        String subtitleKey(@Nullable String source) {
            if (this == RUMOR) return "mc_talking.gossip.rumor";
            return source == null || source.isBlank() ? "mc_talking.gossip.news" : "mc_talking.gossip.news_from";
        }
    }

    static final int DURATION_TICKS = 60;
    static final int MAX_ACTIVE = 2;
    static final double WATCH_RANGE = 16;
    static final double SUBTITLE_RANGE = 12;
    static final long PAIR_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(5);

    private static final class Moment {
        final AbstractEntityCitizen teller;
        final AbstractEntityCitizen listener;
        final List<ConversationManager.CoreActivityReservation> reservations = new ArrayList<>();
        int age;
        boolean ended;

        Moment(AbstractEntityCitizen teller, AbstractEntityCitizen listener) {
            this.teller = teller;
            this.listener = listener;
        }
    }

    private static final List<Moment> ACTIVE = new ArrayList<>();
    private static final Map<Long, Long> LAST_PAIR_MOMENT = new HashMap<>();

    private GossipMoments() {
    }

    /**
     * Shows {@code teller} passing something on to {@code listener}, if a player is watching and
     * both are free. {@code source} names where broadcast news came from, e.g. "the notice board".
     */
    public static boolean start(AbstractEntityCitizen teller, AbstractEntityCitizen listener, Kind kind,
                                @Nullable String source) {
        if (!McTalkingConfig.INSTANCE.instance().showGossipMoments || teller == listener) return false;
        if (!(teller.level() instanceof ServerLevel level) || listener.level() != level) return false;
        if (ACTIVE.size() >= MAX_ACTIVE || teller.getCitizenData() == null || listener.getCitizenData() == null) {
            return false;
        }
        List<ServerPlayer> watching = playersNear(level, teller, listener, WATCH_RANGE);
        if (watching.isEmpty()) return false;
        long pair = pairKey(teller, listener);
        long now = System.currentTimeMillis();
        Long last = LAST_PAIR_MOMENT.get(pair);
        if (last != null && now - last < PAIR_COOLDOWN_MS) return false;
        if (ConversationManager.isCitizenBusy(teller) || ConversationManager.isCitizenBusy(listener)) return false;
        if (ConversationManager.isAsleep(teller) || ConversationManager.isAsleep(listener)) return false;

        Moment moment = new Moment(teller, listener);
        for (AbstractEntityCitizen citizen : List.of(teller, listener)) {
            // A player talking to either of them ends the moment at once.
            var reservation = ConversationManager.reserveCoreActivity(citizen, 10, TimeUnit.SECONDS,
                    () -> end(moment), () -> end(moment));
            if (reservation == null) {
                moment.reservations.forEach(ConversationManager.CoreActivityReservation::close);
                return false;
            }
            moment.reservations.add(reservation);
        }
        LAST_PAIR_MOMENT.put(pair, now);
        if (LAST_PAIR_MOMENT.size() > 512) LAST_PAIR_MOMENT.values().removeIf(at -> now - at > PAIR_COOLDOWN_MS);
        ACTIVE.add(moment);
        AiStatusHelper.setAiStatusSynced(teller, AiStatus.IN_CONVERSATION);
        AiStatusHelper.setAiStatusSynced(listener, AiStatus.IN_CONVERSATION);

        Component subtitle = subtitle(kind, teller.getDisplayName().getString(), listener.getDisplayName().getString(),
                source).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        for (ServerPlayer player : playersNear(level, teller, listener, SUBTITLE_RANGE)) {
            player.displayClientMessage(subtitle, true);
        }
        SpeechTimeline.mark("gossip (" + kind.name().toLowerCase() + "): " + teller.getDisplayName().getString()
                + " -> " + listener.getDisplayName().getString());
        return true;
    }

    private static MutableComponent subtitle(Kind kind, String teller, String listener, @Nullable String source) {
        String key = kind.subtitleKey(source);
        if (key.equals("mc_talking.gossip.news_from")) return Component.translatable(key, teller, listener, source);
        return Component.translatable(key, teller, listener);
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        for (Moment moment : List.copyOf(ACTIVE)) {
            if (++moment.age > DURATION_TICKS || !moment.teller.isAlive() || !moment.listener.isAlive()
                    || moment.teller.isRemoved() || moment.listener.isRemoved()) {
                end(moment);
                continue;
            }
            hold(moment.teller, moment.listener);
            hold(moment.listener, moment.teller);
        }
    }

    private static void hold(AbstractEntityCitizen citizen, AbstractEntityCitizen other) {
        if (!citizen.getNavigation().isDone()) citizen.getNavigation().stop();
        citizen.getLookControl().setLookAt(other, 30, 30);
    }

    private static void end(Moment moment) {
        if (moment.ended) return;
        moment.ended = true;
        ACTIVE.remove(moment);
        moment.reservations.forEach(ConversationManager.CoreActivityReservation::close);
        for (AbstractEntityCitizen citizen : List.of(moment.teller, moment.listener)) {
            // Whoever took the citizen over (a player conversation) sets its own status.
            AiStatusHelper.runOnServerThread(citizen, () -> {
                if (!ConversationManager.isCitizenBusy(citizen)) {
                    AiStatusHelper.setAiStatusOnServerThread(citizen, AiStatus.NONE);
                }
            });
        }
    }

    public static void clear() {
        for (Moment moment : List.copyOf(ACTIVE)) end(moment);
        LAST_PAIR_MOMENT.clear();
    }

    private static List<ServerPlayer> playersNear(ServerLevel level, AbstractEntityCitizen a, AbstractEntityCitizen b,
                                                  double range) {
        double rangeSq = range * range;
        return level.players().stream()
                .filter(player -> !player.isSpectator())
                .filter(player -> player.distanceToSqr(a) <= rangeSq || player.distanceToSqr(b) <= rangeSq)
                .toList();
    }

    private static long pairKey(AbstractEntityCitizen a, AbstractEntityCitizen b) {
        int x = a.getId();
        int y = b.getId();
        return ((long) Math.min(x, y) << 32) | (Math.max(x, y) & 0xFFFFFFFFL);
    }
}
