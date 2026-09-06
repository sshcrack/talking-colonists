package me.sshcrack.mc_talking.conversations.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class MemorySaveCoordinatorTest {
    @Test
    void generationFirstPersistsOnceAfterAuthorization() {
        Queue<Runnable> serverQueue = new ArrayDeque<>();
        AtomicInteger saves = new AtomicInteger();
        AtomicReference<String> persisted = new AtomicReference<>();
        var coordinator = new MemorySaveCoordinator<String>(serverQueue::add, value -> {
            saves.incrementAndGet();
            persisted.set(value);
        });

        coordinator.generationSucceeded("memory");
        assertTrue(serverQueue.isEmpty());
        coordinator.authorizeSave();
        coordinator.authorizeSave();
        assertEquals(1, serverQueue.size());

        serverQueue.remove().run();
        assertEquals(1, saves.get());
        assertEquals("memory", persisted.get());
        assertEquals(MemorySaveCoordinator.Status.SAVED,
                coordinator.completion().toCompletableFuture().join().status());
    }

    @Test
    void authorizationFirstPersistsOnceAfterGeneration() {
        Queue<Runnable> serverQueue = new ArrayDeque<>();
        AtomicInteger saves = new AtomicInteger();
        var coordinator = new MemorySaveCoordinator<String>(serverQueue::add, value -> saves.incrementAndGet());

        coordinator.authorizeSave();
        coordinator.generationSucceeded("memory");
        coordinator.generationSucceeded("duplicate");
        assertEquals(1, serverQueue.size());

        serverQueue.remove().run();
        assertEquals(1, saves.get());
    }

    @Test
    void cancellationBeforePersistenceWins() {
        Queue<Runnable> serverQueue = new ArrayDeque<>();
        AtomicInteger saves = new AtomicInteger();
        var coordinator = new MemorySaveCoordinator<String>(serverQueue::add, value -> saves.incrementAndGet());

        coordinator.generationSucceeded("memory");
        coordinator.authorizeSave();
        assertTrue(coordinator.cancel("shutdown"));
        assertFalse(serverQueue.isEmpty());
        serverQueue.remove().run();

        assertEquals(0, saves.get());
        assertEquals(MemorySaveCoordinator.Status.CANCELLED,
                coordinator.completion().toCompletableFuture().join().status());
    }

    @Test
    void generationFailureNeverPersists() {
        Queue<Runnable> serverQueue = new ArrayDeque<>();
        AtomicInteger saves = new AtomicInteger();
        var coordinator = new MemorySaveCoordinator<String>(serverQueue::add, value -> saves.incrementAndGet());

        coordinator.authorizeSave();
        coordinator.generationFailed("bad response", new IllegalArgumentException("bad"));

        assertTrue(serverQueue.isEmpty());
        assertEquals(0, saves.get());
        assertEquals(MemorySaveCoordinator.Status.FAILED,
                coordinator.completion().toCompletableFuture().join().status());
    }
}
