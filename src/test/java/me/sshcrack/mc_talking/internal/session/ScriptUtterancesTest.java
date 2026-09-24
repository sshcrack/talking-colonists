package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScriptUtterancesTest {
    @Test
    void keepsOnlyTranscriptLinesOfParticipantsWithoutAudioCues() {
        String script = """
                # Audio Profile
                Tomas Reed: gravelly and tired
                ## Scene:
                A windy evening at the well.
                ## Transcript:
                Tomas Reed: [sighs] The well is dry again.
                **Ana Silva:** [brightly] Then we ask the manager for a pump!
                Narrator: This line is not a participant.
                Tomas Reed: []
                """;

        var lines = ScriptUtterances.parse(script, List.of("Tomas Reed", "Ana Silva"));

        assertEquals(List.of(
                new ScriptUtterances.Line("Tomas Reed", "The well is dry again."),
                new ScriptUtterances.Line("Ana Silva", "Then we ask the manager for a pump!")), lines);
    }

    @Test
    void scriptsWithoutATranscriptHeadingAreReadWhole() {
        var lines = ScriptUtterances.parse("Ana Silva: Hi!\nTomas Reed: Hello.", List.of("Tomas Reed", "Ana Silva"));
        assertEquals(2, lines.size());
    }
}
