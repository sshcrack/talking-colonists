package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Ownership-safe busy reservation for addon gameplay that occupies a citizen without opening an
 * AI conversation (for example an errand, courier run, ceremony movement, or defense formation).
 *
 * <p>Close the handle when the activity ends. A stale handle cannot release a newer reservation
 * because core validates its opaque ownership token.</p>
 */
public interface CitizenActivityReservation extends AutoCloseable {
    @NotNull UUID citizenId();

    @NotNull String ownerId();

    boolean isClosed();

    @Override
    void close();
}
