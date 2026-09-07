package me.sshcrack.mc_talking.devtools;

import com.google.gson.JsonParser;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.gemini_live_lib.websocket.WebSocket;
import me.sshcrack.gemini_live_lib.websocket.handshake.ClientHandshake;
import me.sshcrack.gemini_live_lib.websocket.server.WebSocketServer;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationService;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.manager.CitizenWsClient;
import me.sshcrack.mc_talking.manager.audio.CitizenEntityAudioProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Automated in-world regression fixture. Called only by the disposable auto-quit run. */
public final class DevRuntimeVerification {
    private DevRuntimeVerification() { }

    public static CompletableFuture<Void> verify(MinecraftServer server) {
        if (!Boolean.getBoolean("mc_talking.autoQuit")) throw new IllegalStateException("Verification requires auto-quit mode");
        return CompletableFuture.runAsync(() -> {
            ProbeClient ambient = null;
            ProbeClient playerClient = null;
            String savedKey = McTalkingConfig.INSTANCE.instance().geminiApiKey;
            boolean savedMemory = McTalkingConfig.INSTANCE.instance().enableConversationSummaryAndMemorize;
            try (LocalProvider provider = new LocalProvider()) {
                var citizen = server.submit(() -> {
                    // Prevent autonomous fixture activity from using a real provider. Never save
                    // these temporary settings, and never read or print a credential here.
                    McTalkingConfig.INSTANCE.instance().geminiApiKey = "";
                    McTalkingConfig.INSTANCE.instance().enableConversationSummaryAndMemorize = false;
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    var world = player.serverLevel();
                    var pos = player.blockPosition().offset(4, 0, 4);
                    var colony = IColonyManager.getInstance().createColony(world, pos, player, "Verification colony", "");
                    if (colony == null) throw new IllegalStateException("Cannot create fixture colony");
                    var data = colony.getCitizenManager().createAndRegisterCivilianData();
                    var entity = (EntityCitizen) ModEntities.CITIZEN.create(world);
                    if (entity == null) throw new IllegalStateException("Cannot create fixture citizen");
                    entity.setUUID(data.getUUID());
                    entity.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
                    entity.setCitizenId(data.getId());
                    entity.getCitizenColonyHandler().setColonyId(colony.getID());
                    entity.setNoAi(true);
                    world.addFreshEntity(entity);
                    entity.getCitizenColonyHandler().registerWithColony(colony.getID(), data.getId());
                    return entity;
                }).get(10, TimeUnit.SECONDS);

                ambient = server.submit(() -> {
                    var client = new ProbeClient(citizen, provider.uri());
                    var reservation = ConversationManager.reserveAmbientForeground(citizen, ConversationKind.URGENT_CONTACT);
                    require(reservation != null && reservation.attachClient(client), "ambient reservation");
                    client.addOnCloseAction(reservation::close);
                    require(reservation.activate(), "ambient activation");
                    client.addPromptTextAfterTalkingComplete("Say the verification phrase.");
                    return client;
                }).get(10, TimeUnit.SECONDS);
                require("Verified speech.".equals(ambient.heard.poll(10, TimeUnit.SECONDS)), "ambient audible turn");
                require(ambient.ready.poll(2, TimeUnit.SECONDS) != null, "initial provider setup");
                require(server.submit(() -> CitizenConversationService.providerStatus(citizen)
                        .orElseThrow().readyForInput()).get(5, TimeUnit.SECONDS), "addon provider readiness");
                provider.getConnections().iterator().next().close(1012, "injected service restart");
                require(ambient.ready.poll(10, TimeUnit.SECONDS) != null, "provider recovery");
                require(!ambient.isLifecycleClosed(), "reconnect preserved audio/session ownership");
                var resumed = ambient;
                server.submit(() -> resumed.addPromptTextAfterTalkingComplete("Say it again after reconnect.")).get(5, TimeUnit.SECONDS);
                require("Verified speech.".equals(ambient.heard.poll(10, TimeUnit.SECONDS)), "audible turn after reconnect");
                server.submit(() -> { resumed.close(); }).get(5, TimeUnit.SECONDS);
                require(server.submit(() -> !ConversationManager.isCitizenBusy(citizen)).get(5, TimeUnit.SECONDS), "ambient ownership cleanup");
                require(server.submit(() -> CitizenConversationService.providerStatus(citizen).isEmpty())
                        .get(5, TimeUnit.SECONDS), "addon provider cleanup");

                playerClient = server.submit(() -> {
                    var client = new ProbeClient(citizen, provider.uri(), server.getPlayerList().getPlayers().get(0));
                    // Exercises real audio conversion and queue-before-setup using the player client.
                    client.addPromptAudio(new short[4800]);
                    return client;
                }).get(10, TimeUnit.SECONDS);
                require("Verified speech.".equals(playerClient.heard.poll(10, TimeUnit.SECONDS)), "player audio turn");
                var playerProbe = playerClient;
                server.submit(() -> { playerProbe.close(); }).get(5, TimeUnit.SECONDS);
                require(playerClient.isLifecycleClosed(), "player cancellation cleanup");
                McTalking.LOGGER.info("MC_TALKING_RUNTIME_SUCCESS:citizen,prompt,queued-input,audio,reconnect,cleanup");
            } catch (Exception error) {
                throw new IllegalStateException("In-world conversation verification failed", error);
            } finally {
                if (ambient != null) ambient.close();
                if (playerClient != null) playerClient.close();
                McTalkingConfig.INSTANCE.instance().geminiApiKey = savedKey;
                McTalkingConfig.INSTANCE.instance().enableConversationSummaryAndMemorize = savedMemory;
            }
        });
    }

    private static void require(boolean condition, String step) {
        if (!condition) throw new IllegalStateException("Verification failed: " + step);
    }

    private static final class ProbeClient extends CitizenWsClient {
        final LinkedBlockingQueue<String> heard = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<Boolean> ready = new LinkedBlockingQueue<>();
        ProbeClient(AbstractEntityCitizen citizen, URI endpoint) {
            super(citizen, (Consumer<CitizenWsClient>) null);
            uri = endpoint;
        }
        ProbeClient(AbstractEntityCitizen citizen, URI endpoint, ServerPlayer player) {
            super(new CitizenEntityAudioProvider(citizen, null), citizen, player);
            uri = endpoint;
        }
        @Override public void onSetupComplete() { super.onSetupComplete(); ready.add(true); }
        @Override protected void onAudibleTranscriptComplete(String transcript) { heard.add(transcript); }
        @Override public boolean shouldResumeAndSaveSession() { return false; }
    }

    private static final class LocalProvider extends WebSocketServer implements AutoCloseable {
        private final CompletableFuture<Void> started = new CompletableFuture<>();
        LocalProvider() throws Exception {
            super(new InetSocketAddress("127.0.0.1", 0), 1);
            setDaemon(true);
            start();
            started.get(5, TimeUnit.SECONDS);
        }
        URI uri() { return URI.create("ws://127.0.0.1:" + getPort()); }
        @Override public void onStart() { started.complete(null); }
        @Override public void onOpen(WebSocket socket, ClientHandshake handshake) { }
        @Override public void onClose(WebSocket socket, int code, String reason, boolean remote) { }
        @Override public void onError(WebSocket socket, Exception error) { started.completeExceptionally(error); }
        @Override public void onMessage(WebSocket socket, String frame) {
            var message = JsonParser.parseString(frame).getAsJsonObject();
            if (message.has("setup")) {
                var setup = message.getAsJsonObject("setup");
                require(setup.has("systemInstruction") && setup.has("tools"), "real citizen prompt/tools construction");
                socket.send("{\"setupComplete\":{}}".getBytes(StandardCharsets.UTF_8));
            } else if (message.has("realtime_input") || message.has("realtimeInput")) {
                String audio = Base64.getEncoder().encodeToString(new byte[4800]);
                socket.send("{\"serverContent\":{\"modelTurn\":{\"parts\":[{\"inlineData\":{"
                        + "\"mimeType\":\"audio/pcm;rate=24000\",\"data\":\"" + audio + "\"}}]},"
                        + "\"outputTranscription\":{\"text\":\"Verified speech.\"},"
                        + "\"generationComplete\":true,\"turnComplete\":true}}");
            }
        }
        @Override public void close() throws InterruptedException { stop(1000); }
    }
}
