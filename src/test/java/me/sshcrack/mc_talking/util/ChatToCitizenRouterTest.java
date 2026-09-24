package me.sshcrack.mc_talking.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatToCitizenRouterTest {
    @Test
    void prefixedLinesGoToTheCitizenWithoutThePrefix() {
        assertEquals("bake some bread", ChatToCitizenRouter.citizenText("@ bake some bread", "@", false));
        assertEquals("hi", ChatToCitizenRouter.citizenText("  @hi  ", "@", false));
    }

    @Test
    void otherLinesStayInServerChat() {
        assertNull(ChatToCitizenRouter.citizenText("hello everyone", "@", false));
        assertNull(ChatToCitizenRouter.citizenText("hi @Alex", "@", false));
    }

    @Test
    void aBarePrefixSendsNothing() {
        assertNull(ChatToCitizenRouter.citizenText("@", "@", false));
        assertNull(ChatToCitizenRouter.citizenText("@   ", "@", true));
    }

    @Test
    void theToggleSendsEveryLine() {
        assertEquals("hello everyone", ChatToCitizenRouter.citizenText("hello everyone", "@", true));
        assertEquals("prefixed", ChatToCitizenRouter.citizenText("@prefixed", "@", true));
        assertNull(ChatToCitizenRouter.citizenText("   ", "@", true));
    }

    @Test
    void multiCharacterAndEmptyPrefixes() {
        assertEquals("hi", ChatToCitizenRouter.citizenText(">> hi", ">>", false));
        assertNull(ChatToCitizenRouter.citizenText("@hi", "", false));
        assertNull(ChatToCitizenRouter.citizenText("@hi", "  ", false));
        assertEquals("@hi", ChatToCitizenRouter.citizenText("@hi", "", true));
        assertNull(ChatToCitizenRouter.citizenText("@hi", null, false));
    }
}
