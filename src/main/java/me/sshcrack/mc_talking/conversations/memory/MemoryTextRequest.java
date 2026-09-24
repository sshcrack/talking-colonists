package me.sshcrack.mc_talking.conversations.memory;

import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.QuotaRetryInfo;
import me.sshcrack.mc_talking.config.QuotaTracker;
import me.sshcrack.mc_talking.util.BackgroundSlotType;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A JSON memory request: Flash-Lite first, the cheap Live model as a fallback. Flash-Lite has a
 * daily limit; the Live model has none on the free tier, so when Flash-Lite's quota is used up (or
 * its service fails) memory is still written. The Live answer is not schema-constrained, so the
 * schema goes into the prompt and the caller's parser still validates the result.
 */
final class MemoryTextRequest {
    static final long LIVE_SLOT_WAIT_MILLIS = 60_000;
    static final long LIVE_ANSWER_TIMEOUT_SECONDS = 90;

    /** Why a request produced no text. */
    static final class FailedException extends Exception {
        FailedException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    interface Flash {
        String send() throws UnexpectedResponseException, IOException, InterruptedException;
    }

    interface Live {
        String send() throws FailedException, InterruptedException;
    }

    interface Quota {
        boolean flashExhausted();

        boolean liveExhausted();

        void flashExceeded(@Nullable Long retryAfterMillis);

        void flashSucceeded();
    }

    private MemoryTextRequest() {
    }

    /** Runs the request for a memory of {@code citizen} (its background Live slot is used for the fallback). */
    static String generate(AbstractEntityCitizen citizen, MinecraftServer server, String systemPrompt, String userPrompt,
                           JsonObject schema, String logTag) throws FailedException, InterruptedException {
        String apiKey = McTalkingConfig.INSTANCE.instance().geminiApiKey;
        Flash flash = () -> GeminiFlash.sendSimpleFlashRequest(McTalkingConfig.FLASH_MODEL, apiKey, systemPrompt,
                userPrompt, GeminiFlash.GenerateContentRequest.GenerationConfig.json(schema));
        Live live = () -> viaLive(citizen, server, liveSystemPrompt(systemPrompt, schema), userPrompt, logTag);
        return run(flash, McTalkingConfig.INSTANCE.instance().enableLiveTextFallback ? live : null, new Quota() {
            @Override
            public boolean flashExhausted() {
                return QuotaTracker.isQuotaExceeded(McTalkingConfig.FLASH_MODEL);
            }

            @Override
            public boolean liveExhausted() {
                return QuotaTracker.isQuotaExceeded(McTalkingConfig.CHEAP_LIVE_MODEL.getName());
            }

            @Override
            public void flashExceeded(@Nullable Long retryAfterMillis) {
                QuotaTracker.reportQuotaExceeded(McTalkingConfig.FLASH_MODEL, retryAfterMillis);
            }

            @Override
            public void flashSucceeded() {
                QuotaTracker.reportSuccess(McTalkingConfig.FLASH_MODEL);
            }
        }, logTag);
    }

    /**
     * Flash first; the Live fallback (when given) on Flash quota exhaustion, a 429, a server error
     * or a network failure. Other Flash errors (bad request, bad key) are not retried on Live.
     */
    static String run(Flash flash, @Nullable Live live, Quota quota, String logTag)
            throws FailedException, InterruptedException {
        Exception flashError = null;
        if (quota.flashExhausted()) {
            McTalking.LOGGER.info("{} Flash-Lite quota is used up", logTag);
        } else {
            try {
                String text = flash.send();
                quota.flashSucceeded();
                return text;
            } catch (UnexpectedResponseException error) {
                if (error.getStatusCode() == 429) {
                    quota.flashExceeded(QuotaRetryInfo.parseRetryDelayMs(error.getResponseBody()));
                } else if (error.getStatusCode() < 500) {
                    throw new FailedException("Flash-Lite rejected the request (HTTP " + error.getStatusCode() + ")", error);
                }
                flashError = error;
            } catch (IOException error) {
                flashError = error;
            }
        }

        if (live == null) {
            throw new FailedException("Flash-Lite is unavailable and the Live fallback is off", flashError);
        }
        if (quota.liveExhausted()) {
            throw new FailedException("Flash-Lite and the Live model are both out of quota", flashError);
        }
        McTalking.LOGGER.info("{} Falling back to the Live model for this memory request", logTag);
        return live.send();
    }

    /** The Live model has no response schema, so the prompt carries it. */
    static String liveSystemPrompt(String systemPrompt, JsonObject schema) {
        return systemPrompt + "\n\nReply with only one JSON object that matches this JSON Schema. "
                + "No Markdown, no explanation, nothing before or after the JSON.\n" + schema;
    }

    private static String viaLive(AbstractEntityCitizen citizen, MinecraftServer server, String systemPrompt,
                                  String userPrompt, String logTag) throws FailedException, InterruptedException {
        ConversationManager.BackgroundReservation reservation = reserveSlot(citizen, server);
        if (reservation == null) {
            throw new FailedException("No background Live session was free within "
                    + LIVE_SLOT_WAIT_MILLIS / 1000 + " s", null);
        }
        CompletableFuture<String> answer = new CompletableFuture<>();
        var data = citizen.getCitizenData();
        LiveTextClient client = new LiveTextClient(systemPrompt, userPrompt, citizen.getUUID(),
                data != null && data.isFemale(), logTag, answer::complete,
                () -> answer.completeExceptionally(new IllegalStateException("Live text request failed")));
        try {
            if (!reservation.attachClient(client)) throw new FailedException("Background Live slot was lost", null);
            client.connect();
            return answer.get(LIVE_ANSWER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException error) {
            throw new FailedException("Live fallback produced no answer", error);
        } finally {
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // Already closed.
            }
            reservation.close();
        }
    }

    /** Waits for the citizen's background Live slot, so the fallback never exceeds the session limits. */
    @Nullable
    private static ConversationManager.BackgroundReservation reserveSlot(AbstractEntityCitizen citizen,
                                                                         MinecraftServer server)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + LIVE_SLOT_WAIT_MILLIS;
        while (true) {
            ConversationManager.BackgroundReservation reservation;
            try {
                reservation = server.submit(() -> ConversationManager.reserveBackgroundSlot(citizen,
                        BackgroundSlotType.COMPACTION)).get(10, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException error) {
                return null;
            }
            if (reservation != null || System.currentTimeMillis() >= deadline) return reservation;
            Thread.sleep(2_000);
        }
    }
}
