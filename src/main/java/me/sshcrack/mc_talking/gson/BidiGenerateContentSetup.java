package me.sshcrack.mc_talking.gson;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BidiGenerateContentSetup {
    @NotNull
    public String model;
    @NotNull
    public GenerationConfig generationConfig = new GenerationConfig();

    public BidiGenerateContentSetup(@NotNull String model) {
        this.model = model;
    }


    public static class GenerationConfig {
        @Nullable
        public String candidateCount;
        @Nullable
        public String maxOutputTokens;
        @Nullable
        public String temperature;
        @Nullable
        public String topP;
        @Nullable
        public String topK;
        @Nullable
        public String presencePenalty;
        @Nullable
        public String frequencyPenalty;
        @Nullable
        public List<String> responseModalities;
        @Nullable
        public SpeechConfig speechConfig;
        @Nullable
        public Object mediaResolution;

        public static class SpeechConfig {
            @Nullable
            public String language_code;

            @Nullable
            public VoiceConfig voice_config;


            public static class VoiceConfig {
                @Nullable
                public PrebuiltVoiceConfig prebuiltVoiceConfig;
            }

            public static class PrebuiltVoiceConfig {
                @Nullable
                public String voice_name;
            }
        }
    }

    public static final List<String> MALE_VOICES = List.of("Puck", "Charon", "Fenir", "Orus");
    public static final List<String> FEMALE_VOICES = List.of("Kore", "Aoede", "Leda", "Zephyr");

    public static class SystemInstruction {
        public List<Part> parts = new ArrayList<>();

        public static class Part {
            @NotNull
            public String text;

            public Part(@NotNull String text) {
                this.text = text;
            }
        }
    }

    public SystemInstruction systemInstruction;
    public List<Object> tools;
}
