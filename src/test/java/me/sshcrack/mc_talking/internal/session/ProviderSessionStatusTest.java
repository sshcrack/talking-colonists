package me.sshcrack.mc_talking.internal.session;

import me.sshcrack.mc_talking.api.conversation.ProviderSessionStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderSessionStatusTest {
    @Test
    void readinessCannotContradictProviderState() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderSessionStatus(
                ProviderSessionStatus.State.CONNECTING, true, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProviderSessionStatus(
                ProviderSessionStatus.State.ACTIVE, true, -1));
        assertTrue(new ProviderSessionStatus(ProviderSessionStatus.State.ACTIVE, true, 1).readyForInput());
    }
}
