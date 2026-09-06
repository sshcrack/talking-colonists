package me.sshcrack.mc_talking.api.prompt;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** One bounded, typed block of addon prompt context. */
public record PromptContribution(
        @NotNull String section,
        @NotNull String text,
        @NotNull PromptContributionKind kind
) {
    public PromptContribution {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        if (section.isBlank()) throw new IllegalArgumentException("section must not be blank");
        if (text.isBlank()) throw new IllegalArgumentException("text must not be blank");
    }

    public static PromptContribution observation(String section, String text) {
        return new PromptContribution(section, text, PromptContributionKind.OBSERVATION);
    }

    public static PromptContribution recollection(String section, String text) {
        return new PromptContribution(section, text, PromptContributionKind.RECOLLECTION);
    }

    public static PromptContribution instruction(String section, String text) {
        return new PromptContribution(section, text, PromptContributionKind.INSTRUCTION);
    }
}
