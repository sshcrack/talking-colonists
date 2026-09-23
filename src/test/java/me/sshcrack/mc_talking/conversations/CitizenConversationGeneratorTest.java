package me.sshcrack.mc_talking.conversations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenConversationGeneratorTest {
    @Test
    void scriptPromptPinsConfiguredLanguage() {
        String prompt = CitizenConversationGenerator.conversationSystemPrompt("Portuguese");

        assertTrue(prompt.contains("Write every transcript line in Portuguese"), prompt);
    }
}
