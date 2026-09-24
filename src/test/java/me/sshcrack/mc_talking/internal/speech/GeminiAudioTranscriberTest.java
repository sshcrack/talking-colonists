package me.sshcrack.mc_talking.internal.speech;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiAudioTranscriberTest {
    @Test
    void downsamplesByAveraging() {
        short[] pcm = {3, 6, 9, 30, 60, 90, 1};
        assertArrayEquals(new short[]{6, 60}, GeminiAudioTranscriber.downsample(pcm));
    }

    @Test
    void writesAMonoSixteenBitWav() {
        byte[] wav = GeminiAudioTranscriber.wav(new short[]{1, -1}, 16_000);
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(48, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4, StandardCharsets.US_ASCII));
        assertEquals(40, header.getInt(4));
        assertEquals("WAVEfmt ", new String(wav, 8, 8, StandardCharsets.US_ASCII));
        assertEquals(1, header.getShort(20), "PCM");
        assertEquals(1, header.getShort(22), "mono");
        assertEquals(16_000, header.getInt(24));
        assertEquals(32_000, header.getInt(28));
        assertEquals(16, header.getShort(34));
        assertEquals("data", new String(wav, 36, 4, StandardCharsets.US_ASCII));
        assertEquals(4, header.getInt(40));
        assertEquals(1, header.getShort(44));
        assertEquals(-1, header.getShort(46));
    }

    @Test
    void requestHasThePromptAndInlineAudio() {
        JsonObject body = GeminiAudioTranscriber.requestBody(new short[960]);
        var parts = body.getAsJsonArray("contents").get(0).getAsJsonObject().getAsJsonArray("parts");

        assertEquals(GeminiAudioTranscriber.PROMPT, parts.get(0).getAsJsonObject().get("text").getAsString());
        JsonObject audio = parts.get(1).getAsJsonObject().getAsJsonObject("inline_data");
        assertEquals("audio/wav", audio.get("mime_type").getAsString());
        byte[] wav = Base64.getDecoder().decode(audio.get("data").getAsString());
        assertEquals(44 + 320 * 2, wav.length, "20 ms at 16 kHz");
    }

    @Test
    void parsesTheTranscript() {
        String response = """
                {"candidates":[{"content":{"parts":[{"text":"Hello "},{"text":"there. "}],"role":"model"}}]}
                """;
        assertEquals("Hello there.", GeminiAudioTranscriber.parseTranscript(response));
    }

    @Test
    void noSpeechMarkerAndEmptyResponsesAreBlank() {
        assertEquals("", GeminiAudioTranscriber.parseTranscript(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"[No speech]\"}]}}]}"));
        assertEquals("", GeminiAudioTranscriber.parseTranscript("{\"candidates\":[]}"));
        assertEquals("", GeminiAudioTranscriber.parseTranscript("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}"));
    }
}
