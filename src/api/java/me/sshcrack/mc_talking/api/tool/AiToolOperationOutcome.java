package me.sshcrack.mc_talking.api.tool;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Terminal or immediate outcome metadata for an addon AI tool operation. */
public record AiToolOperationOutcome(
        @NotNull String operationId,
        @NotNull UUID sessionId,
        @NotNull String toolId,
        @NotNull String callId,
        @NotNull AiToolOperationStatus status,
        @Nullable JsonObject result,
        @Nullable String error,
        boolean deliveredToSession
) {
    public AiToolOperationOutcome {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(toolId, "toolId");
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(status, "status");
        result = result == null ? null : result.deepCopy();
    }
}
