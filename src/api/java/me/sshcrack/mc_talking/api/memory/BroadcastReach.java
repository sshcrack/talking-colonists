package me.sshcrack.mc_talking.api.memory;

/**
 * How far a published broadcast has spread through its colony; see
 * {@link CitizenMemoryService#broadcastReach}.
 *
 * @param heard    citizens who currently remember the broadcast
 * @param citizens citizens in the colony
 */
public record BroadcastReach(int heard, int citizens) {
    public BroadcastReach {
        if (heard < 0 || citizens < 0 || heard > citizens) {
            throw new IllegalArgumentException("heard must be between 0 and citizens");
        }
    }

    /** Whether every citizen of the colony has heard it. */
    public boolean everyone() {
        return citizens > 0 && heard == citizens;
    }
}
