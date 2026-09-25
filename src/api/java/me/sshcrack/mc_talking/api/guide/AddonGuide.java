package me.sshcrack.mc_talking.api.guide;

import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.registration.NamespacedAddonId;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * A short guide to an addon feature ({@link ApiFeature#ADDON_GUIDES}). It becomes a chapter of the
 * Colony Handbook, and citizens know it in player conversations, so they can explain the feature in
 * their own words when asked.
 *
 * <p>Write it for players who have never heard of the feature: what it is, then how to start with
 * blocks and items they can get in survival. Commands belong in {@code notes}, marked as operator
 * commands.</p>
 *
 * @param id      namespaced id, e.g. {@code "tc_townhall:elections"}; chapters are sorted by it
 * @param title   the chapter title, at most {@link #MAX_TITLE} characters
 * @param summary what the feature is, one or two sentences, at most {@link #MAX_SUMMARY} characters
 * @param steps   how to start, in order; at most {@link #MAX_ITEMS} steps of at most {@link #MAX_ITEM} characters
 * @param notes   optional details (settings, operator commands); same limits as {@code steps}
 */
public record AddonGuide(@NotNull String id, @NotNull String title, @NotNull String summary,
                         @NotNull List<String> steps, @NotNull List<String> notes) {
    public static final int MAX_TITLE = 40;
    public static final int MAX_SUMMARY = 300;
    public static final int MAX_ITEM = 200;
    public static final int MAX_ITEMS = 6;

    public AddonGuide {
        id = NamespacedAddonId.require(id, "id");
        title = requireText(title, "title", MAX_TITLE);
        summary = requireText(summary, "summary", MAX_SUMMARY);
        steps = requireItems(steps, "steps");
        notes = requireItems(notes, "notes");
    }

    /** A guide without notes. */
    public AddonGuide(@NotNull String id, @NotNull String title, @NotNull String summary, @NotNull List<String> steps) {
        this(id, title, summary, steps, List.of());
    }

    private static String requireText(String value, String label, int max) {
        Objects.requireNonNull(value, label);
        String text = value.strip();
        if (text.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        if (text.length() > max) throw new IllegalArgumentException(label + " must be at most " + max + " characters");
        return text;
    }

    private static List<String> requireItems(List<String> items, String label) {
        Objects.requireNonNull(items, label);
        if (items.size() > MAX_ITEMS) throw new IllegalArgumentException(label + " must have at most " + MAX_ITEMS + " entries");
        return items.stream().map(item -> requireText(item, label + " entry", MAX_ITEM)).toList();
    }
}
