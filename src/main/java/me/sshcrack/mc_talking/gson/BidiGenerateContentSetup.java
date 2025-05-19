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
        public Object speechConfig;
        @Nullable
        public Object mediaResolution;
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
    public List<Object> tools;
}
