package me.sshcrack.mc_talking.conversations.complaints;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** A citizen's complaint history towards the player of a prompt: one note per active problem, and residues. */
public record ComplaintContext(Map<ComplaintTopic, ComplaintHistory.Note> notes, List<ComplaintHistory.Residue> residues) {
    public @Nullable ComplaintHistory.Note note(@Nullable ComplaintTopic topic) {
        return topic == null ? null : notes.get(topic);
    }

    /** Implemented by prompt views that carry a complaint context (core's own snapshots). */
    public interface Holder {
        @Nullable ComplaintContext complaints();
    }
}
