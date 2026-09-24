package me.sshcrack.mc_talking.internal.speech;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * One text-only {@code generateContent} request per capture: the prompt plus the audio as an
 * inline WAV part. The Gemini Live library's Flash client only sends text parts, so this builds
 * the request itself. The key goes in a header, never in the URL or in logs.
 */
public final class GeminiAudioTranscriber implements SpeechCaptureRuntime.Transcriber {
    /** The model's answer when the audio has no intelligible speech. */
    static final String NO_SPEECH_MARKER = "[no speech]";
    static final String PROMPT = "Transcribe what the speaker says in this audio, word for word, in the language "
            + "they speak. Reply with the transcript only: no quotes, labels, timestamps or descriptions of sounds. "
            + "If there is no intelligible speech, reply exactly " + NO_SPEECH_MARKER + ".";
    /** 48 kHz from Simple Voice Chat, averaged down to 16 kHz: speech needs no more, and the upload is a third. */
    static final int OUTPUT_SAMPLE_RATE = 16_000;
    private static final int DOWNSAMPLE_FACTOR = SpeechCaptureRuntime.SAMPLE_RATE / OUTPUT_SAMPLE_RATE;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String model;
    private final Supplier<String> apiKey;

    public GeminiAudioTranscriber(String model, Supplier<String> apiKey) {
        this.model = model;
        this.apiKey = apiKey;
    }

    @Override
    public CompletableFuture<String> transcribe(short[] pcm) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey.get())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(pcm).toString(), StandardCharsets.UTF_8))
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() == 429) {
                        throw new SpeechCaptureRuntime.QuotaExceededException("The transcription model's quota is exhausted");
                    }
                    if (response.statusCode() / 100 != 2) {
                        throw new IllegalStateException("Transcription request failed with HTTP " + response.statusCode());
                    }
                    return parseTranscript(response.body());
                });
    }

    static JsonObject requestBody(short[] pcm) {
        JsonObject audio = new JsonObject();
        audio.addProperty("mime_type", "audio/wav");
        audio.addProperty("data", Base64.getEncoder().encodeToString(wav(downsample(pcm), OUTPUT_SAMPLE_RATE)));
        JsonObject audioPart = new JsonObject();
        audioPart.add("inline_data", audio);
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", PROMPT);

        JsonArray parts = new JsonArray();
        parts.add(textPart);
        parts.add(audioPart);
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0);
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("generationConfig", generationConfig);
        return body;
    }

    /** The concatenated text parts of the first candidate; blank for no speech. */
    static String parseTranscript(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) return "";
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null || !content.has("parts")) return "";
        StringBuilder text = new StringBuilder();
        for (JsonElement part : content.getAsJsonArray("parts")) {
            JsonElement value = part.getAsJsonObject().get("text");
            if (value != null) text.append(value.getAsString());
        }
        String transcript = text.toString().strip();
        return transcript.equalsIgnoreCase(NO_SPEECH_MARKER) ? "" : transcript;
    }

    static short[] downsample(short[] pcm) {
        short[] out = new short[pcm.length / DOWNSAMPLE_FACTOR];
        for (int i = 0; i < out.length; i++) {
            int sum = 0;
            for (int j = 0; j < DOWNSAMPLE_FACTOR; j++) sum += pcm[i * DOWNSAMPLE_FACTOR + j];
            out[i] = (short) (sum / DOWNSAMPLE_FACTOR);
        }
        return out;
    }

    /** 16-bit mono PCM WAV. */
    static byte[] wav(short[] pcm, int sampleRate) {
        int dataBytes = pcm.length * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataBytes);
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeInt(out, 36 + dataBytes);
        out.writeBytes("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        writeInt(out, 16);
        writeShort(out, 1);
        writeShort(out, 1);
        writeInt(out, sampleRate);
        writeInt(out, sampleRate * 2);
        writeShort(out, 2);
        writeShort(out, 16);
        out.writeBytes("data".getBytes(StandardCharsets.US_ASCII));
        writeInt(out, dataBytes);
        for (short sample : pcm) writeShort(out, sample);
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
}
