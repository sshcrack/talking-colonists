package me.sshcrack.mc_talking.internal.tool;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthenticatedActorResolverTest {
    @Test
    void currentLiveActorAlwaysWinsOverDetachedFallback() {
        UUID id = UUID.randomUUID();
        AtomicReference<UUID> lookedUp = new AtomicReference<>();

        String resolved = AuthenticatedActorResolver.resolve(id, requested -> {
            lookedUp.set(requested);
            return "reconnected-player";
        }, "fake-fallback");

        assertEquals(id, lookedUp.get());
        assertEquals("reconnected-player", resolved);
    }

    @Test
    void vettedDetachedActorIsUsedWhenLiveLookupHasNoEntry() {
        UUID id = UUID.randomUUID();
        assertEquals("fake-player",
                AuthenticatedActorResolver.resolve(id, ignored -> null, "fake-player"));
    }

    @Test
    void missingLiveActorWithoutDetachedFallbackFailsClosed() {
        UUID id = UUID.randomUUID();
        assertNull(AuthenticatedActorResolver.resolve(id, ignored -> null, null));
    }
}
