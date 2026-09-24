package me.sshcrack.mc_talking.internal.session;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Extracts the spoken lines of a generated pair-conversation script (roadmap A5). */
public final class ScriptUtterances {
    public record Line(@NotNull String speaker, @NotNull String text) {
    }

    private ScriptUtterances() {
    }

    /**
     * Returns every {@code "<participant>: text"} line in order, without square-bracket audio cues.
     * When the script has a "Transcript" heading, only lines after it count, so voice profiles written
     * as {@code "Name: description"} are not mistaken for speech.
     */
    public static @NotNull List<Line> parse(@NotNull String script, @NotNull List<String> participantNames) {
        List<Line> lines = new ArrayList<>();
        String[] rawLines = script.split("\\R");
        int start = 0;
        for (int i = 0; i < rawLines.length; i++) {
            if (rawLines[i].strip().matches("(?i)#+\\s*transcript:?\\s*")) {
                start = i + 1;
                break;
            }
        }
        for (int i = start; i < rawLines.length; i++) {
            String raw = rawLines[i];
            String line = raw.strip();
            for (String name : participantNames) {
                String prefix = name + ":";
                String bold = "**" + name + ":**";
                String rest = line.startsWith(bold) ? line.substring(bold.length())
                        : line.startsWith(prefix) ? line.substring(prefix.length()) : null;
                if (rest == null) continue;
                String text = rest.replaceAll("\\[[^\\]]*]", " ").strip().replaceAll("\\s+", " ");
                if (!text.isEmpty()) lines.add(new Line(name, text));
                break;
            }
        }
        return lines;
    }
}
