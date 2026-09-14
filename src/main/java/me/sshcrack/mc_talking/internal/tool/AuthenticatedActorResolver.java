package me.sshcrack.mc_talking.internal.tool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Live-first actor resolution with an explicitly vetted detached fallback. */
public final class AuthenticatedActorResolver {
    private AuthenticatedActorResolver() {
    }

    public static <T> @Nullable T resolve(
            @NotNull UUID actorId,
            @NotNull Function<UUID, T> liveLookup,
            @Nullable T detachedFallback
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(liveLookup, "liveLookup");
        T live = liveLookup.apply(actorId);
        return live != null ? live : detachedFallback;
    }
}
