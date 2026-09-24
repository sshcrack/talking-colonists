package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressCooldownsTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AddressCooldowns cooldowns = new AddressCooldowns(now::get);
    private final UUID player = UUID.randomUUID();
    private final UUID citizen = UUID.randomUUID();

    @Test
    void playerIsLeftAloneForTheCooldownAfterBeingAddressed() {
        assertTrue(cooldowns.mayAddress(player, 150));
        cooldowns.recordAddressed(player);
        now.addAndGet(149_000);
        assertFalse(cooldowns.mayAddress(player, 150));
        now.addAndGet(1_000);
        assertTrue(cooldowns.mayAddress(player, 150));
    }

    @Test
    void citizenCooldownIsSeparateFromThePlayers() {
        cooldowns.recordCitizenContact(citizen);
        assertTrue(cooldowns.mayAddress(player, 150));
        assertFalse(cooldowns.citizenMayContact(citizen, 600));
        now.addAndGet(600_000);
        assertTrue(cooldowns.citizenMayContact(citizen, 600));
    }

    @Test
    void zeroDisablesAndForgettingResets() {
        cooldowns.recordAddressed(player);
        assertTrue(cooldowns.mayAddress(player, 0));
        cooldowns.forgetPlayer(player);
        assertTrue(cooldowns.mayAddress(player, 150));
    }
}
