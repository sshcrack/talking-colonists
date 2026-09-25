package me.sshcrack.mc_talking.api.intro;

import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.registration.NamespacedAddonId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Something a citizen tells each player about once, when it becomes relevant
 * ({@link ApiFeature#INTRODUCTIONS}). A citizen walks up to the player in their colony and raises it
 * in a sentence or two, for example inviting them to the campfire at their first dusk. Introductions
 * wait for Talking Colonists' own welcome, come one at a time, and respect the cooldown between
 * unprompted lines to a player.
 *
 * @param id       namespaced id, e.g. {@code "tc_campfire:first_dusk"}; each player hears an id once, so
 *                 never reuse one for something else
 * @param topic    what it is about, completing "Anna tells you about ...", e.g. "campfire nights"; at
 *                 most {@link #MAX_TOPIC} characters. Shown in chat when the citizen cannot speak.
 * @param lineHint what the citizen should get across, written as an instruction to them, e.g. "Invite
 *                 them to the campfire tonight: at dusk citizens gather there to tell stories."; at most
 *                 {@link #MAX_HINT} characters
 * @param guideId  the Colony Handbook chapter ({@code AddonGuide} id) with more, or null
 * @param trigger  when it becomes relevant
 */
public record Introduction(@NotNull String id, @NotNull String topic, @NotNull String lineHint,
                           @Nullable String guideId, @NotNull IntroductionTrigger trigger) {
    public static final int MAX_TOPIC = 60;
    public static final int MAX_HINT = 300;

    public Introduction {
        id = NamespacedAddonId.require(id, "id");
        topic = requireText(topic, "topic", MAX_TOPIC);
        lineHint = requireText(lineHint, "lineHint", MAX_HINT);
        if (guideId != null) guideId = NamespacedAddonId.require(guideId, "guideId");
        Objects.requireNonNull(trigger, "trigger");
    }

    private static String requireText(String value, String label, int max) {
        Objects.requireNonNull(value, label);
        String text = value.strip();
        if (text.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        if (text.length() > max) throw new IllegalArgumentException(label + " must be at most " + max + " characters");
        return text;
    }
}
