package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderInputBufferTest {
    @Test
    void setupRacingWithFirstInputCannotStrandThePrompt() throws Exception {
        var buffer = new ProviderInputBuffer();
        var checked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var flushed = new CountDownLatch(1);
        var ready = new AtomicBoolean();
        var sent = new ArrayList<String>();
        Thread sender = new Thread(() -> buffer.submit("urgent prompt", () -> {
            boolean value = ready.get();
            checked.countDown();
            try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            return value;
        }, sent::add));
        sender.start();
        try {
            assertTrue(checked.await(2, TimeUnit.SECONDS));
            ready.set(true);
            Thread setup = new Thread(() -> {
                buffer.flush(ready::get, sent::add);
                flushed.countDown();
            });
            setup.start();
            release.countDown();
            assertTrue(flushed.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("urgent prompt"), sent);
            setup.join(2000);
        } finally { release.countDown(); sender.join(2000); }
    }

    @Test
    void failedSendRetainsOrderingAcrossReconnectAndCloseRejectsLateInput() {
        var buffer = new ProviderInputBuffer();
        var sent = new ArrayList<String>();
        buffer.submit("attribution", () -> false, sent::add);
        buffer.submit("audio", () -> false, sent::add);
        assertThrows(IllegalStateException.class, () -> buffer.flush(() -> true, frame -> {
            if (frame.equals("audio")) throw new IllegalStateException("disconnected");
            sent.add(frame);
        }));
        buffer.flush(() -> true, sent::add);
        buffer.close();
        buffer.submit("late callback", () -> true, sent::add);
        assertEquals(List.of("attribution", "audio"), sent);
    }
    @Test
    void recoveryPreservesValidOrderingButDropsStaleMicrophoneFrames() {
        var buffer = new ProviderInputBuffer();
        var now = new java.util.concurrent.atomic.AtomicLong();
        var ready = new AtomicBoolean(false);
        var sent = new ArrayList<String>();

        buffer.submit("speech-1", 100, now::get, ready::get, sent::add);
        now.set(40);
        buffer.submit("speech-2", 100, now::get, ready::get, sent::add);
        buffer.submit("attribution", Long.MAX_VALUE, now::get, ready::get, sent::add);
        assertEquals(3, buffer.size());

        now.set(120);
        ready.set(true);
        ProviderInputBuffer.DrainResult result = buffer.flush(ready::get, sent::add, now::get);
        assertEquals(1, result.droppedExpired());
        assertEquals(List.of("speech-2", "attribution"), sent);
        assertEquals(0, result.remaining());
    }

}
