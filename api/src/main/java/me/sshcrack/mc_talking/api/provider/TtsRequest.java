package me.sshcrack.mc_talking.api.provider;

import java.util.Objects;

public final class TtsRequest {
    private final String text;
    private final String voice;
    private final String language;

    public TtsRequest(String text, String voice, String language) {
        this.text = Objects.requireNonNullElse(text, "");
        this.voice = voice;
        this.language = Objects.requireNonNullElse(language, "en-US");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String text() {
        return text;
    }

    public String voice() {
        return voice;
    }

    public String language() {
        return language;
    }

    public static final class Builder {
        private String text = "";
        private String voice;
        private String language = "en-US";

        public Builder text(String text) {
            this.text = Objects.requireNonNullElse(text, "");
            return this;
        }

        public Builder voice(String voice) {
            this.voice = voice;
            return this;
        }

        public Builder language(String language) {
            this.language = Objects.requireNonNullElse(language, "en-US");
            return this;
        }

        public TtsRequest build() {
            return new TtsRequest(text, voice, language);
        }
    }
}
