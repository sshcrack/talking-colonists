package me.sshcrack.mc_talking.conversations.complaints;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What one citizen has told which player about which problem: how often, since when, and whether the
 * player responded. The {@link ComplaintStage} follows from it: a first mention is constructive, a
 * problem raised again and again without a fix makes the citizen frustrated, and in the end they give
 * up asking. Days are colony days. Thread-safe; saved with the citizen's memories.
 */
public final class ComplaintHistory {
    /** Days after a fix during which a frustrated citizen is still a little sore about it. */
    static final int RESIDUE_DAYS = 2;

    /**
     * One problem raised with one player.
     *
     * @param raised       how many days the citizen raised it (at most once a day counts)
     * @param answered     how many of those times the player responded
     * @param answeredLast whether the player responded the last time
     */
    public record Entry(ComplaintTopic topic, UUID player, int raised, int answered, boolean answeredLast,
                        int firstDay, int lastDay) {
    }

    /** A problem that got fixed while the citizen was frustrated with the player about it. */
    public record Residue(ComplaintTopic topic, UUID player, int raised, int fixedDay) {
    }

    /** What the prompt needs to know about one problem and one player. */
    public record Note(ComplaintTopic topic, ComplaintStage stage, int raised, int answered, int firstDay,
                       boolean raisedToday, boolean brokenPromise, boolean progressVisible) {
    }

    private record Key(ComplaintTopic topic, UUID player) {
    }

    private final Map<Key, Entry> entries = new LinkedHashMap<>();
    private final Map<Key, Residue> residues = new LinkedHashMap<>();

    /**
     * The citizen raised {@code topic} with {@code player}. Counts once a day, so a walk-up and the
     * conversation that follows are one complaint. Returns whether it counted.
     */
    public synchronized boolean recordRaised(ComplaintTopic topic, UUID player, int day) {
        Key key = new Key(topic, player);
        Entry before = entries.get(key);
        if (before != null && before.lastDay() == day) return false;
        residues.remove(key);
        entries.put(key, before == null
                ? new Entry(topic, player, 1, 0, false, day, day)
                : new Entry(topic, player, before.raised() + 1, before.answered(), false, before.firstDay(), day));
        return true;
    }

    /** The player said something to the citizen: it answers what the citizen raised with them today. */
    public synchronized void recordAnswered(UUID player, int day) {
        entries.replaceAll((key, entry) -> key.player().equals(player) && entry.lastDay() == day && !entry.answeredLast()
                ? new Entry(entry.topic(), player, entry.raised(), entry.answered() + 1, true, entry.firstDay(), entry.lastDay())
                : entry);
    }

    /**
     * Forgets every problem that is no longer active. One the citizen was frustrated about leaves a
     * residue for a few days; old residues expire.
     */
    public synchronized void resolve(Set<ComplaintTopic> active, int day, ComplaintPace pace) {
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var next = iterator.next();
            Entry entry = next.getValue();
            if (active.contains(entry.topic())) continue;
            iterator.remove();
            if (stage(entry, day, pace, false).ordinal() >= ComplaintStage.FRUSTRATED.ordinal()) {
                residues.put(next.getKey(), new Residue(entry.topic(), entry.player(), entry.raised(), day));
            }
        }
        residues.values().removeIf(residue -> day - residue.fixedDay() > RESIDUE_DAYS);
    }

    public synchronized Note note(ComplaintTopic topic, UUID player, int day, ComplaintPace pace, boolean progressVisible) {
        Entry entry = entries.get(new Key(topic, player));
        if (entry == null) return new Note(topic, ComplaintStage.FIRST_MENTION, 0, 0, day, false, false, progressVisible);
        return new Note(topic, stage(entry, day, pace, progressVisible), entry.raised(), entry.answered(),
                entry.firstDay(), entry.lastDay() == day, entry.answered() > 0 && day > entry.lastDay(), progressVisible);
    }

    /** Stage of a problem the player has not heard about is {@link ComplaintStage#FIRST_MENTION}. */
    public ComplaintStage stage(ComplaintTopic topic, UUID player, int day, ComplaintPace pace) {
        return note(topic, player, day, pace, false).stage();
    }

    /**
     * Each time raised counts, each time ignored counts again, and the personality shifts it. A reply
     * buys a day of grace, and visible progress (it is being built) keeps the citizen at a reminder.
     */
    static ComplaintStage stage(Entry entry, int day, ComplaintPace pace, boolean progressVisible) {
        int ignored = entry.raised() - entry.answered();
        int score = entry.raised() + ignored + pace.shift;
        ComplaintStage stage = score <= 2 ? ComplaintStage.REMINDER
                : score <= 5 ? ComplaintStage.FRUSTRATED
                : ComplaintStage.RESIGNED;
        if (pace == ComplaintPace.WITHDRAWN && stage == ComplaintStage.FRUSTRATED) stage = ComplaintStage.RESIGNED;
        if (entry.answeredLast() && day - entry.lastDay() < 1) stage = stage.atMost(ComplaintStage.REMINDER);
        if (progressVisible) stage = stage.atMost(ComplaintStage.REMINDER);
        return stage;
    }

    public synchronized List<Residue> residues(UUID player) {
        return residues.values().stream().filter(residue -> residue.player().equals(player)).toList();
    }

    public synchronized @Nullable Entry entry(ComplaintTopic topic, UUID player) {
        return entries.get(new Key(topic, player));
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty() && residues.isEmpty();
    }

    // ── Saving ──────────────────────────────────────────────────────────────

    public synchronized List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }

    public synchronized List<Residue> allResidues() {
        return new ArrayList<>(residues.values());
    }

    public synchronized void load(List<Entry> savedEntries, List<Residue> savedResidues) {
        entries.clear();
        residues.clear();
        for (Entry entry : savedEntries) entries.put(new Key(entry.topic(), entry.player()), entry);
        for (Residue residue : savedResidues) residues.put(new Key(residue.topic(), residue.player()), residue);
    }
}
