package me.sshcrack.mc_talking.api.text;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Outcome of a {@link CitizenTextService} request.
 *
 * @param status what happened
 * @param text   generated text for {@link Status#SUCCESS}; for structured requests, the raw JSON text
 * @param json   parsed object for successful structured requests, otherwise null
 * @param detail human-readable reason for failures (never contains credentials)
 */
public record TextResult(@NotNull Status status, @Nullable String text, @Nullable JsonObject json,
                         @Nullable String detail) {
    public enum Status {
        SUCCESS,
        /** The Flash model's quota is exhausted; retry after it resets. */
        QUOTA,
        /** Too many text requests are running; retry shortly. */
        NO_CAPACITY,
        /** The model answered, but not with usable text or JSON matching the schema. */
        INVALID_OUTPUT,
        /** The request was cancelled, e.g. because the server is stopping. */
        CANCELLED,
        /** No Gemini API key is configured, or the citizen/colony is not available. */
        UNAVAILABLE,
        /** The provider request failed for another reason (network, server error). */
        PROVIDER_ERROR
    }

    public TextResult {
        Objects.requireNonNull(status, "status");
        if ((status == Status.SUCCESS) != (text != null)) {
            throw new IllegalArgumentException("text is set exactly when the status is SUCCESS");
        }
        if (json != null) json = json.deepCopy();
    }

    public static @NotNull TextResult success(@NotNull String text, @Nullable JsonObject json) {
        return new TextResult(Status.SUCCESS, text, json, null);
    }

    public static @NotNull TextResult failure(@NotNull Status status, @Nullable String detail) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("use success(...)");
        return new TextResult(status, null, null, detail);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    @Override
    public @Nullable JsonObject json() {
        return json == null ? null : json.deepCopy();
    }
}
