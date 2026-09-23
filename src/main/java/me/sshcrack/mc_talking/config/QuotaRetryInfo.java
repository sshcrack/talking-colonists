package me.sshcrack.mc_talking.config;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort extraction of a provider-supplied retry delay from a raw Gemini error response
 * body, so a player-visible "reset time" can be more than a local guess when the API actually
 * tells us one.
 *
 * <p>Google API errors carry this as a {@code google.rpc.RetryInfo} detail, e.g.
 * {@code "details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"31s"}]}.
 * We deliberately do a narrow regex match instead of a full JSON parse: the response body is
 * untrusted, we only need one field, and this keeps the parser trivially testable without a
 * dependency on the exact error DTO shape.</p>
 */
public final class QuotaRetryInfo {
    private QuotaRetryInfo() {
    }

    private static final Pattern RETRY_DELAY_SECONDS = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"");

    /**
     * @param responseBody raw response body, or {@code null}
     * @return the retry delay in milliseconds if one could be found, otherwise {@code null}
     */
    @Nullable
    public static Long parseRetryDelayMs(@Nullable String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return null;
        Matcher matcher = RETRY_DELAY_SECONDS.matcher(responseBody);
        if (!matcher.find()) return null;
        try {
            double seconds = Double.parseDouble(matcher.group(1));
            return Math.round(seconds * 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
