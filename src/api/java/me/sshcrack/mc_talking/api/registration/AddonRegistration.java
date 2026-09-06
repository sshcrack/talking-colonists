package me.sshcrack.mc_talking.api.registration;

import org.jetbrains.annotations.NotNull;

/**
 * Lifetime handle for an addon registration.
 *
 * <p>Registrations remain active until this handle is closed. Closing is idempotent and only
 * unregisters the exact registration represented by this handle, so a stale handle can never
 * remove a later registration that reused the same ID.</p>
 */
public interface AddonRegistration extends AutoCloseable {
    /** Namespaced registration ID, for example {@code my_addon:expedition}. */
    @NotNull String id();

    /** Returns whether this handle has already been closed. */
    boolean isClosed();

    /** Unregisters this exact registration. Safe to call repeatedly. */
    @Override
    void close();
}
