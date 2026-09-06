package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.UUID;

/**
 * Ownership-safe, bounded busy lease for addon gameplay that occupies a citizen without opening an
 * AI conversation (for example an errand, courier run, ceremony movement, or defense formation).
 *
 * <p>Close the handle when the activity ends. A stale handle cannot release or renew a newer
 * reservation because core validates its opaque ownership token. Leases also expire automatically;
 * long-running activities should periodically {@link #renew(Duration)}.</p>
 */
public interface CitizenActivityReservation extends AutoCloseable {
    @NotNull UUID citizenId();

    @NotNull String ownerId();

    boolean isClosed();

    /** Renews this exact lease. Returns false if it already expired/closed or ownership changed. */
    boolean renew(@NotNull Duration timeout);

    @Override
    void close();
}
