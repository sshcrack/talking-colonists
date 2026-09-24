package me.sshcrack.mc_talking.api.text;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An in-character text generation request for {@link CitizenTextService}.
 *
 * @param purpose          short tag such as {@code "gazette:headline"}, used for logs and quota accounting
 * @param directive        what to write, e.g. "Write a two-sentence notice about the harvest festival"
 * @param maxChars         optional upper bound on the returned text; longer output is trimmed
 * @param responseSchema   optional JSON schema (Gemini structured output); the result then carries parsed JSON
 */
public record TextRequest(
        @NotNull String purpose,
        @NotNull String directive,
        @Nullable Integer maxChars,
        @Nullable JsonObject responseSchema
) {
    public static final int MAX_DIRECTIVE_LENGTH = 4_000;
    public static final int MAX_OUTPUT_CHARS = 8_000;
    private static final Pattern PURPOSE = Pattern.compile("[a-z0-9_.:-]{1,64}");

    public TextRequest {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(directive, "directive");
        if (!PURPOSE.matcher(purpose).matches()) {
            throw new IllegalArgumentException("purpose must match " + PURPOSE.pattern());
        }
        directive = directive.strip();
        if (directive.isEmpty()) throw new IllegalArgumentException("directive must not be blank");
        if (directive.length() > MAX_DIRECTIVE_LENGTH) {
            throw new IllegalArgumentException("directive exceeds " + MAX_DIRECTIVE_LENGTH + " characters");
        }
        if (maxChars != null && (maxChars < 1 || maxChars > MAX_OUTPUT_CHARS)) {
            throw new IllegalArgumentException("maxChars must be within [1, " + MAX_OUTPUT_CHARS + "]");
        }
        if (responseSchema != null) responseSchema = responseSchema.deepCopy();
    }

    public static @NotNull TextRequest of(@NotNull String purpose, @NotNull String directive) {
        return new TextRequest(purpose, directive, null, null);
    }

    public @NotNull TextRequest withMaxChars(int maxChars) {
        return new TextRequest(purpose, directive, maxChars, responseSchema);
    }

    /** Asks for JSON output matching {@code schema}; the result's {@link TextResult#json()} holds it. */
    public @NotNull TextRequest withResponseSchema(@NotNull JsonObject schema) {
        return new TextRequest(purpose, directive, maxChars, Objects.requireNonNull(schema, "schema"));
    }

    @Override
    public @Nullable JsonObject responseSchema() {
        return responseSchema == null ? null : responseSchema.deepCopy();
    }
}
