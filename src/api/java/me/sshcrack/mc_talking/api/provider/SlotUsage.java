package me.sshcrack.mc_talking.api.provider;

/**
 * Concurrent Live session slots of one pool.
 *
 * @param used slots currently held
 * @param max  configured limit
 */
public record SlotUsage(int used, int max) {
    public SlotUsage {
        used = Math.max(0, used);
        max = Math.max(0, max);
    }

    public int available() {
        return Math.max(0, max - used);
    }
}
