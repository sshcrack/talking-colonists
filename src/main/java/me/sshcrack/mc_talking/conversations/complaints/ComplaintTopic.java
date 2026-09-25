package me.sshcrack.mc_talking.conversations.complaints;

import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** A lasting problem a citizen can raise with a player. */
public enum ComplaintTopic {
    HOUSING("your home"),
    WORK("having no job"),
    HEALTH("your illness"),
    SUPPLIES("missing tools or supplies at work"),
    FOOD("the food"),
    SECURITY("the colony's safety");

    private final String description;

    ComplaintTopic(String description) {
        this.description = description;
    }

    /** Completes "your problem with ...", e.g. "your home". */
    public String description() {
        return description;
    }

    /** The id used by the {@code raise_concern} tool and in saved data. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable ComplaintTopic byId(String id) {
        for (ComplaintTopic topic : values()) {
            if (topic.id().equalsIgnoreCase(id.strip())) return topic;
        }
        return null;
    }

    /** The topic a happiness modifier stands for, or null for moods nobody complains to the player about. */
    public static @Nullable ComplaintTopic of(HappinessModifierType type) {
        return switch (type) {
            case HOMELESSNESS -> HOUSING;
            case UNEMPLOYMENT -> WORK;
            case HEALTH -> HEALTH;
            case IDLE_AT_JOB -> SUPPLIES;
            case FOOD -> FOOD;
            case SECURITY -> SECURITY;
            default -> null;
        };
    }
}
