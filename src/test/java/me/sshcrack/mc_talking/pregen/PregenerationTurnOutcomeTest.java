package me.sshcrack.mc_talking.pregen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PregenerationTurnOutcomeTest {
    @Test
    void silentTurnIsRetriedOnceThenFails() {
        assertEquals(PregenerationGeminiClient.TurnOutcome.DELIVER, PregenerationGeminiClient.TurnOutcome.after(4_800, false));
        assertEquals(PregenerationGeminiClient.TurnOutcome.RETRY, PregenerationGeminiClient.TurnOutcome.after(0, false));
        assertEquals(PregenerationGeminiClient.TurnOutcome.DELIVER, PregenerationGeminiClient.TurnOutcome.after(4_800, true));
        assertEquals(PregenerationGeminiClient.TurnOutcome.FAIL, PregenerationGeminiClient.TurnOutcome.after(0, true));
    }
}
