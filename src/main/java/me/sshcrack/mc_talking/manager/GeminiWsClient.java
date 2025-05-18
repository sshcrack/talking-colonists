package me.sshcrack.mc_talking.manager;

import com.google.gson.JsonParser;
import me.sshcrack.mc_talking.Config;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import me.sshcrack.mc_talking.MinecoloniesTalkingCitizens;
import me.sshcrack.mc_talking.gson.BidiGenerateContentSetup;
import me.sshcrack.mc_talking.gson.ClientContent;
import me.sshcrack.mc_talking.gson.ClientMessages;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class GeminiWsClient extends WebSocketClient {
    boolean setupComplete;
    boolean wasConnectedOnce = false;
    GeminiStream stream;
    TalkingManager manager;
    @Nullable
    private String pending_prompt = null;

    private static String getUrl() {
        return "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + Config.GEMINI_API_KEY.get();
    }

    public GeminiWsClient(TalkingManager manager) {
        super(URI.create(getUrl()));
        this.manager = manager;
        stream = new GeminiStream(manager.channel);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        var setup = new BidiGenerateContentSetup("models/gemini-2.0-flash-live-001");
        setup.generationConfig.responseModalities = List.of("AUDIO");

        send(ClientMessages.setup(setup));
    }

    @Override
    public void onMessage(String message) {
        if (message.contains("\"setupComplete\"")) {
            MinecoloniesTalkingCitizens.LOGGER.info("Gemini setup complete");
            setupComplete = true;
            if (pending_prompt != null) {
                addPrompt(pending_prompt);
                pending_prompt = null;
            }
            return;
        }

        if (!setupComplete)
            return;

        var p = JsonParser.parseString(message);
        if (!p.isJsonObject())
            return;

        var outer = p.getAsJsonObject();
        if (outer.has("serverContent") && outer.get("serverContent").isJsonObject()) {
            var obj = outer.getAsJsonObject("serverContent");
            if (obj.has("turnComplete") && obj.get("turnComplete").getAsBoolean()) {
                System.out.println("Turn done");
                return;
            }

            if (obj.has("modelTurn")) {
                var modelTurn = obj.getAsJsonObject("modelTurn");
                if (modelTurn.has("parts")) {
                    var parts = modelTurn.getAsJsonArray("parts");
                    for (var part : parts) {
                        if (!part.isJsonObject())
                            continue;

                        var pObj = part.getAsJsonObject();
                        if (!pObj.has("inlineData") || !pObj.get("inlineData").isJsonObject())
                            continue;

                        var inlineData = pObj.getAsJsonObject("inlineData");
                        if (!inlineData.has("data") || !inlineData.get("data").isJsonPrimitive())
                            continue;

                        var mimeType = inlineData.get("mimeType").getAsString();
                        if (!mimeType.contains("audio/pcm")) {
                            MinecoloniesTalkingCitizens.LOGGER.warn("Invalid mime type: " + inlineData.get("mimeType").getAsString());
                            continue;
                        }

                        var sampleRateStr = mimeType.split("rate=")[1];
                        var sampleRate = Integer.parseInt(sampleRateStr);

                        var data = Base64.getDecoder().decode(inlineData.get("data").getAsString());
                        stream.addGeminiPcm(data, sampleRate);
                    }
                }
            } else {
                System.out.println("Unknown message: " + message);
            }
        }
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        String newContent = new String(bytes.array(), StandardCharsets.UTF_8);
        onMessage(newContent);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        MinecoloniesTalkingCitizens.LOGGER.info("GeminiWsClient closed: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        MinecoloniesTalkingCitizens.LOGGER.error("Error in GeminiWsClient", ex);
    }

    public void addPrompt(String message) {
        var content = new ClientContent();
        var turn = new ClientContent.Turn();
        turn.parts.add(new ClientContent.Turn.Part(message));
        content.turns.add(turn);
        content.turn_complete = true;

        if (!setupComplete) {
            MinecoloniesTalkingCitizens.LOGGER.info("Queueing prompt: " + message);
            pending_prompt = message;
            if (!this.isOpen()) {
                if (wasConnectedOnce)
                    reconnect();
                else {
                    connect();
                    wasConnectedOnce = true;
                }
            }
            return;
        }

        MinecoloniesTalkingCitizens.LOGGER.info("Sending prompt: " + message);
        send(ClientMessages.content(content));
    }

    public boolean canPrompt() {
        return this.setupComplete && McTalkingVoicechatPlugin.vcApi != null;
    }

    @Override
    public void close() {
        stream.close();
        super.close();
    }
}
