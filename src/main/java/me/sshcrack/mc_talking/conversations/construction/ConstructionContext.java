package me.sshcrack.mc_talking.conversations.construction;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The colony's building jobs for one prompt. */
public record ConstructionContext(List<ConstructionSite> sites) {
    public ConstructionContext {
        sites = List.copyOf(sites);
    }

    /** A prompt view that carries the colony's building jobs. */
    public interface Holder {
        @Nullable ConstructionContext construction();
    }
}
