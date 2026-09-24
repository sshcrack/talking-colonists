package me.sshcrack.mc_talking.api.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerConversationOptionsTest {
    @Test
    void defaultsAreRecognisedAndWithersChangeOneField() {
        PlayerConversationOptions defaults = PlayerConversationOptions.defaults();
        assertTrue(defaults.isDefault());
        assertNull(defaults.agenda());
        assertNull(defaults.purpose());
        assertTrue(defaults.extractMemory());
        assertTrue(defaults.tools().allowAllAddonTools());

        PlayerConversationOptions judge = defaults.withPurpose("courts:judge").withMemoryExtraction(false);
        assertFalse(judge.isDefault());
        assertEquals("courts:judge", judge.purpose());
        assertFalse(judge.extractMemory());
        assertTrue(defaults.withAgenda("   ").isDefault(), "a blank agenda counts as none");
    }

    @Test
    void rejectsBadPurposeAndOverlongAgenda() {
        PlayerConversationOptions defaults = PlayerConversationOptions.defaults();
        assertThrows(IllegalArgumentException.class, () -> defaults.withPurpose("Quest Giver"));
        assertThrows(IllegalArgumentException.class, () -> defaults.withPurpose(""));
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withAgenda("x".repeat(PlayerConversationOptions.MAX_AGENDA_LENGTH + 1)));
        assertEquals("tour", defaults.withAgenda("  tour  ").agenda());
    }
}
