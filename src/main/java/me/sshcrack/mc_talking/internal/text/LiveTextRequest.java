package me.sshcrack.mc_talking.internal.text;

import com.google.gson.JsonObject;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.conversations.memory.LiveTextClient;
import me.sshcrack.mc_talking.util.BackgroundSlotType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A blocking one-turn text request through the cheap Live model, inside a background Live slot so
 * it never exceeds the configured session limits. Used as the fallback when Flash-Lite is used up.
 * Call from a worker thread, never the server thread.
 */
public final class LiveTextRequest {
    static final long SLOT_WAIT_MILLIS = 60_000;
    static final long ANSWER_TIMEOUT_SECONDS = 90;

    public static final class FailedException extends Exception {
        public FailedException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    private LiveTextRequest() {
    }

    /**
     * @param slotOwner the background slot key: a citizen UUID, or a fresh UUID for colony-wide text
     * @param voiceSeed citizen UUID the (unplayed) voice is chosen from, or null
     */
    public static String send(UUID slotOwner, @Nullable UUID voiceSeed, boolean female, String systemPrompt,
                              String userPrompt, String logTag) throws FailedException, InterruptedException {
        ConversationManager.BackgroundReservation reservation = reserveSlot(slotOwner);
        if (reservation == null) {
            throw new FailedException("No background Live session was free within " + SLOT_WAIT_MILLIS / 1000 + " s", null);
        }
        CompletableFuture<String> answer = new CompletableFuture<>();
        LiveTextClient client = new LiveTextClient(systemPrompt, userPrompt, voiceSeed, female, logTag, answer::complete,
                () -> answer.completeExceptionally(new IllegalStateException("Live text request failed")));
        try {
            if (!reservation.attachClient(client)) throw new FailedException("Background Live slot was lost", null);
            client.connect();
            return answer.get(ANSWER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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

    /** The Live model has no response schema, so the prompt carries it. */
    public static String withSchema(String systemPrompt, JsonObject schema) {
        return systemPrompt + "\n\nReply with only one JSON object that matches this JSON Schema. "
                + "No Markdown, no explanation, nothing before or after the JSON.\n" + schema;
    }

    @Nullable
    private static ConversationManager.BackgroundReservation reserveSlot(UUID owner) throws InterruptedException {
        long deadline = System.currentTimeMillis() + SLOT_WAIT_MILLIS;
        while (true) {
            ConversationManager.BackgroundReservation reservation =
                    ConversationManager.reserveBackgroundSlot(owner, BackgroundSlotType.COMPACTION);
            if (reservation != null || System.currentTimeMillis() >= deadline) return reservation;
            Thread.sleep(2_000);
        }
    }
}
