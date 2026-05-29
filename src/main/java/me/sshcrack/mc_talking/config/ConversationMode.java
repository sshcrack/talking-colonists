package me.sshcrack.mc_talking.config;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

/**
 * Selects how citizen-to-citizen conversations are generated.
 *
 * <ul>
 *   <li>{@link #LIVE_WEBSOCKETS} – Two Gemini Live WebSocket sessions
 *       are opened and their audio output is cross-fed as input to each other, so they
 *       have a real-time spoken conversation without any extra Flash or TTS calls.</li>
 *   <li>{@link #FLASH_TTS} – The original, higher-quality pipeline: Gemini Flash generates
 *       a script, then Gemini TTS renders the multi-speaker audio.</li>
 *   <li>{@link #AUTO} (default) – Tries {@link #FLASH_TTS} first and automatically falls
 *       back to {@link #LIVE_WEBSOCKETS} if the pipeline fails (e.g. quota exhausted).</li>
 * </ul>
 */
public enum ConversationMode implements NameableEnum {
    /**
     * Two Gemini Live WebSocket sessions feed each other.
     * Cheaper and faster; no separate Flash or TTS call.
     */
    LIVE_WEBSOCKETS,

    /**
     * Flash generates the script; Gemini TTS renders multi-speaker audio.
     * Higher quality but uses more API quota.
     */
    FLASH_TTS,

    /**
     * Tries Flash+TTS first; falls back to Live WebSockets on failure.
     * Quota and transient failures are tracked with a cooldown to avoid
     * repeated attempts after the daily quota is exhausted.
     */
    AUTO;

    @Override
    public Component getDisplayName() {
        return Component.literal(name());
    }
}
