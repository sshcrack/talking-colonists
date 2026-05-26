package me.sshcrack.mc_talking.config;

/**
 * Enumeration for background music playback modes.
 * Controls how (and whether) background music is played during conversations.
 */
public enum MusicPlaybackMode {
    /**
     * Music is disabled entirely.
     */
    OFF,

    /**
     * Server-side playback: music is streamed to all players via voice chat.
     * Requires yt-dlp installed on the server.
     */
    SERVER_SIDE,

    /**
     * Client-side playback: each player plays music locally based on metadata hints.
     * (Future mode, not fully implemented yet)
     */
    CLIENT_SIDE
}
