package me.sshcrack.mc_talking.internal.audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.jetbrains.annotations.Nullable;

/**
 * The Simple Voice Chat server API, once voice chat has started. Set and cleared by the voice chat
 * plugin; read from the server, WebSocket and audio threads, hence volatile. Voice chat starts after
 * the Minecraft server, so callers that can run early must handle {@link #get()} returning null.
 */
public final class VoicechatAccess {
    private static volatile @Nullable VoicechatServerApi api;

    private VoicechatAccess() {
    }

    /** The API, or null before voice chat has started or after it stopped. */
    public static @Nullable VoicechatServerApi get() {
        return api;
    }

    public static boolean isReady() {
        return api != null;
    }

    /** The API; use where voice chat must already be running (an active conversation, a playing clip). */
    public static VoicechatServerApi require() {
        VoicechatServerApi current = api;
        if (current == null) throw new IllegalStateException("Simple Voice Chat server API is not available");
        return current;
    }

    public static void set(@Nullable VoicechatServerApi value) {
        api = value;
    }
}
