package me.sshcrack.mc_talking.api.prompt;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** One bounded, typed block of addon prompt context with explicit provenance. */
public record PromptContribution(
        @NotNull String source,
        @NotNull String section,
        @NotNull String text,
        @NotNull PromptContributionKind kind
) {
    private static final int MAX_LABEL_CHARS = 128;

    public PromptContribution {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        if (section.isBlank()) throw new IllegalArgumentException("section must not be blank");
        if (text.isBlank()) throw new IllegalArgumentException("text must not be blank");
        if (source.indexOf('\n') >= 0 || source.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("source must be a single line");
        }
        if (section.indexOf('\n') >= 0 || section.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("section must be a single line");
        }
        if (source.length() > MAX_LABEL_CHARS) {
            throw new IllegalArgumentException("source must be at most " + MAX_LABEL_CHARS + " characters");
        }
        if (section.length() > MAX_LABEL_CHARS) {
            throw new IllegalArgumentException("section must be at most " + MAX_LABEL_CHARS + " characters");
        }
    }

    public static PromptContribution observation(String source, String section, String text) {
        return new PromptContribution(source, section, text, PromptContributionKind.OBSERVATION);
    }

    public static PromptContribution recollection(String source, String section, String text) {
        return new PromptContribution(source, section, text, PromptContributionKind.RECOLLECTION);
    }

    public static PromptContribution instruction(String source, String section, String text) {
        return new PromptContribution(source, section, text, PromptContributionKind.INSTRUCTION);
    }
}
