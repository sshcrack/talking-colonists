package me.sshcrack.mc_talking.conversations.memory;

import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories.CompactionSnapshot;

/** Immutable provider input: the previous summary is part of every subsequent compaction. */
public final class MemoryCompactionPrompt {
    private MemoryCompactionPrompt() { }

    public static String render(String citizenName, CompactionSnapshot snapshot) {
        StringBuilder text = new StringBuilder("Summarize these memories for ").append(citizenName).append(":\n\n");
        if (!snapshot.summary().isBlank()) text.append("Previous summary:\n").append(snapshot.summary()).append("\n\n");
        text.append("Events:\n");
        snapshot.events().forEach(event -> text.append("- ").append(event).append('\n'));
        text.append("\nFacts:\n");
        snapshot.facts().forEach(fact -> text.append("- ").append(fact).append('\n'));
        return text.append("\nSummary:").toString();
    }
}
