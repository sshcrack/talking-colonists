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
    public List<Tool> tools = new ArrayList<>();

    public static class Tool {
        public List<FunctionDeclaration> functionDeclarations = new ArrayList<>();

        //TODO rest of the fields
        public static class FunctionDeclaration {
            @NotNull
            public String name;
            @NotNull
            public String description;


            public FunctionDeclaration(@NotNull String name, @NotNull String description) {
                this.name = name;
                this.description = description;
            }
        }
    }

    public SessionResumptionConfig sessionResumption;

    public static class SessionResumptionConfig {
        public String handle;

        public SessionResumptionConfig(@NotNull String handle) {
            this.handle = handle;
        }

        public SessionResumptionConfig() {
            this.handle = null;
        }
    }

    public RealtimeInputConfig realtimeInputConfig;
    public static class RealtimeInputConfig {
        //TODO rest of the fields
        public TurnCoverage turnCoverage;

        public enum TurnCoverage {
            TURN_COVERAGE_UNSPECIFIED,
            TURN_INCLUDES_ONLY_ACTIVITY,
            TURN_INCLUDES_ALL_INPUT
        }
    }
}
