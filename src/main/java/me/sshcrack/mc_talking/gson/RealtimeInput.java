package me.sshcrack.mc_talking.gson;

import org.jetbrains.annotations.Nullable;

import java.util.Base64;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class RealtimeInput {
    @Nullable
    public Blob audio;

    @Nullable
    public String text;

    public static class Blob {
        public String data;
        public String mime_type;

        public Blob(String mimeType, short[] data) {
            this.mime_type = mimeType;
            this.data = Base64.getEncoder().encodeToString(vcApi.getAudioConverter().shortsToBytes(data));
        }
    }
}
