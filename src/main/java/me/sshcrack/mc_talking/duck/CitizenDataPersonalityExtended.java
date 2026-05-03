package me.sshcrack.mc_talking.duck;

import me.sshcrack.mc_talking.config.PersonalityArchetype;
import org.jetbrains.annotations.Nullable;

/**
 * Duck interface mixed into {@code CitizenData} to persist and expose a
 * citizen's randomly-assigned personality archetype.
 *
 * <p>Exactly one of {@link #mc_talking$getPersonality()} and
 * {@link #mc_talking$getCustomPersonality()} will be non-null for any
 * citizen that has been assigned a personality.</p>
 */
public interface CitizenDataPersonalityExtended {

    /**
     * The built-in archetype assigned to this citizen, or {@code null} if
     * the citizen has a custom archetype string or hasn't been assigned yet.
     */
    @Nullable
    PersonalityArchetype mc_talking$getPersonality();

    /**
     * The freeform custom archetype text assigned to this citizen, or {@code null}
     * if the citizen uses a built-in archetype or hasn't been assigned yet.
     */
    @Nullable
    String mc_talking$getCustomPersonality();

    /**
     * Lazily assigns a personality to this citizen on first call.
     * Subsequent calls are no-ops. Thread-safe enough for server tick context.
     */
    void mc_talking$assignPersonality();
}
