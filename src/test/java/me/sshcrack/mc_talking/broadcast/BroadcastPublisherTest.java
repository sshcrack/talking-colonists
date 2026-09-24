package me.sshcrack.mc_talking.broadcast;

import me.sshcrack.mc_talking.api.memory.BroadcastPublishResult;
import me.sshcrack.mc_talking.api.memory.BroadcastRequest;
import me.sshcrack.mc_talking.api.memory.BroadcastSource;
import me.sshcrack.mc_talking.api.memory.CitizenBroadcastMemoryView;
import me.sshcrack.mc_talking.api.memory.MemoryProvenance;
import me.sshcrack.mc_talking.conversations.memory.MemorySnapshotFactory;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import me.sshcrack.mc_talking.testing.CitizenPromptViewFixture;
import me.sshcrack.mc_talking.testing.TestPromptProviders;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroadcastPublisherTest {
    private static final BroadcastPublisher.Settings ENABLED = new BroadcastPublisher.Settings(true, 20);
    private static final BroadcastSource BOARD = BroadcastSource.block(new BlockPos(10, 64, 10), "the notice board");

    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final BroadcastPublisher publisher = new BroadcastPublisher(clock::get);
    private final FakeColony colony = new FakeColony("Maria", "Joao", "Ana");

    @Test
    void immediateBroadcastReachesEveryCitizen() {
        var result = publisher.publish(colony, BroadcastRequest.immediate(BOARD, "Harvest festival tonight"), ENABLED);

        assertTrue(result.isPublished());
        assertEquals(3, result.recipients());
        for (var citizen : colony.citizens) {
            var broadcast = citizen.memories().getReceivedBroadcasts().get(0);
            assertEquals(result.broadcastId(), broadcast.getId());
            assertEquals("the notice board", broadcast.getOriginatorName());
        }
    }

    @Test
    void propagateFromCitizenSeedsOnlyThatCitizen() {
        var maria = colony.citizens.get(0);
        var result = publisher.publish(colony,
                BroadcastRequest.fromCitizen(BroadcastSource.player(UUID.randomUUID(), "Steve"), "Build walls", maria.citizenId()),
                ENABLED);

        assertEquals(1, result.recipients());
        assertEquals("Maria", maria.memories().getReceivedBroadcasts().get(0).getOriginatorName());
        assertTrue(colony.citizens.get(1).memories().getReceivedBroadcasts().isEmpty());
    }

    @Test
    void propagateFromPositionSeedsNearbyCitizens() {
        colony.nearby.add(colony.citizens.get(2));
        var result = publisher.publish(colony, BroadcastRequest.fromPosition(BOARD, "Meeting at noon", new BlockPos(0, 64, 0)), ENABLED);

        assertEquals(1, result.recipients());
        assertEquals(1, colony.citizens.get(2).memories().getReceivedBroadcasts().size());
        assertTrue(colony.citizens.get(0).memories().getReceivedBroadcasts().isEmpty());
    }

    @Test
    void missingOriginOrDisabledPublishesNothing() {
        assertEquals(BroadcastPublishResult.Status.NO_RECIPIENTS, publisher.publish(colony,
                BroadcastRequest.fromCitizen(BOARD, "Hello", UUID.randomUUID()), ENABLED).status());
        assertEquals(BroadcastPublishResult.Status.NO_RECIPIENTS, publisher.publish(colony,
                BroadcastRequest.fromPosition(BOARD, "Hello", BlockPos.ZERO), ENABLED).status());
        assertEquals(BroadcastPublishResult.Status.DISABLED, publisher.publish(colony,
                BroadcastRequest.immediate(BOARD, "Hello"), new BroadcastPublisher.Settings(false, 20)).status());
        assertTrue(colony.citizens.get(0).memories().getReceivedBroadcasts().isEmpty());
    }

    @Test
    void rateLimitIsPerColonyAndSlides() {
        for (int i = 0; i < BroadcastPublisher.MAX_PUBLISHES_PER_WINDOW; i++) {
            assertTrue(publisher.publish(colony, BroadcastRequest.immediate(BOARD, "News " + i), ENABLED).isPublished());
        }
        assertEquals(BroadcastPublishResult.Status.RATE_LIMITED,
                publisher.publish(colony, BroadcastRequest.immediate(BOARD, "One too many"), ENABLED).status());
        assertTrue(publisher.publish(new FakeColony("Other"), BroadcastRequest.immediate(BOARD, "Elsewhere"), ENABLED).isPublished());

        clock.addAndGet(BroadcastPublisher.RATE_WINDOW_MS);
        assertTrue(publisher.publish(colony, BroadcastRequest.immediate(BOARD, "Later"), ENABLED).isPublished());
    }

    @Test
    void retractionRemovesTheBroadcastEverywhereAndStopsItComingBack() {
        var result = publisher.publish(colony, BroadcastRequest.immediate(BOARD, "Wrong date"), ENABLED);

        assertTrue(publisher.retract(colony, result.broadcastId()));
        assertFalse(publisher.retract(colony, result.broadcastId()));
        for (var citizen : colony.citizens) {
            assertTrue(citizen.memories().getReceivedBroadcasts().isEmpty());
            assertTrue(citizen.memories().hasHeardBroadcast(result.broadcastId()), "retracted ids must not be re-heard");
        }
    }

    @Test
    void expiredBroadcastsLeaveSnapshotsAndArePurged() {
        publisher.publish(colony, BroadcastRequest.immediate(BOARD, "Sale today").withExpiry(Duration.ofMinutes(5)), ENABLED);
        var memories = colony.citizens.get(0).memories();
        long expiresAt = memories.getReceivedBroadcasts().get(0).getExpiresAtMs();
        assertEquals(clock.get() + Duration.ofMinutes(5).toMillis(), expiresAt);

        assertFalse(memories.getReceivedBroadcasts().get(0).isExpired(expiresAt - 1));
        assertTrue(memories.getReceivedBroadcasts().get(0).isExpired(expiresAt));
        assertEquals(1, memories.purgeExpiredBroadcasts(expiresAt));
        assertTrue(memories.getReceivedBroadcasts().isEmpty());
    }

    @Test
    void snapshotsHideBroadcastsThatAlreadyExpired() {
        var memories = new CitizenMemories();
        memories.addBroadcast(BroadcastPublisher.toBroadcast("old", "the notice board",
                BroadcastRequest.immediate(BOARD, "Old news").withExpiry(Duration.ofSeconds(1)), 0L), 20);

        assertTrue(MemorySnapshotFactory.create(memories).broadcasts().isEmpty());
    }

    @Test
    void provenanceDistinguishesPlayerAndAddonSources() {
        var player = BroadcastPublisher.toBroadcast("p", "Maria",
                BroadcastRequest.immediate(BroadcastSource.player(null, "Steve"), "Hi"), 0L);
        var addon = BroadcastPublisher.toBroadcast("a", "Town hall",
                BroadcastRequest.immediate(BroadcastSource.addon("elections", "the town hall"), "Vote"), 0L);

        assertEquals(MemoryProvenance.PLAYER_STATEMENT, player.getProvenance());
        assertNull(player.getSourceLabel());
        assertEquals(MemoryProvenance.ADDON_DIRECT_WRITE, addon.getProvenance());
        assertEquals("the town hall", addon.getSourceLabel());
    }

    @Test
    void newFieldsSurviveSaveAndLoadAndOldSavesStillLoad() {
        var memories = new CitizenMemories();
        publisher.publish(new SingleColony(memories),
                BroadcastRequest.immediate(BOARD, "Quiet hours").withExpiry(Duration.ofHours(1)).withAnnounceAloud(false), ENABLED);

        var reloaded = new CitizenMemories();
        reloaded.deserializeNbt(memories.serializeNbt());
        var broadcast = reloaded.getReceivedBroadcasts().get(0);
        assertEquals("Quiet hours", broadcast.getMessage());
        assertEquals(MemoryProvenance.ADDON_DIRECT_WRITE, broadcast.getProvenance());
        assertEquals("the notice board", broadcast.getSourceLabel());
        assertEquals(clock.get() + Duration.ofHours(1).toMillis(), broadcast.getExpiresAtMs());
        assertFalse(broadcast.isAnnounceAloud());

        var legacyTag = new ColonyBroadcast("x", "Maria", "Old", 5L, "Steve").serialize();
        for (String key : List.of("provenance", "source_label", "expires_at", "announce")) legacyTag.remove(key);
        var legacy = ColonyBroadcast.deserialize(legacyTag);
        assertEquals(MemoryProvenance.PLAYER_STATEMENT, legacy.getProvenance());
        assertEquals(0L, legacy.getExpiresAtMs());
        assertTrue(legacy.isAnnounceAloud());
    }

    @Test
    void immediateBroadcastAppearsInTheNextPromptWithItsSource() {
        TestPromptProviders.installDefault();
        var memories = colony.citizens.get(0).memories();
        publisher.publish(colony, BroadcastRequest.immediate(BOARD, "Harvest festival tonight"), ENABLED);

        String prompt = PromptRuntime.generateCitizenRoleplayPrompt(
                CitizenPromptViewFixture.citizen().memories(MemorySnapshotFactory.create(memories)).build());
        assertTrue(prompt.contains("- the notice board announced: Harvest festival tonight"), prompt);
    }

    @Test
    void requestsValidateScopeOriginAndLength() {
        assertThrows(IllegalArgumentException.class, () -> BroadcastRequest.immediate(BOARD, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> BroadcastRequest.immediate(BOARD, "x".repeat(BroadcastRequest.MAX_MESSAGE_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> BroadcastRequest.immediate(BOARD, "Hi").withExpiry(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new BroadcastRequest("Hi", BOARD,
                me.sshcrack.mc_talking.api.memory.BroadcastScope.PROPAGATE_FROM, null, null, null, true));
        assertThrows(IllegalArgumentException.class, () -> BroadcastSource.addon(" ", "Board"));
        assertEquals("Trimmed", BroadcastRequest.immediate(BOARD, "  Trimmed ").message());
    }

    @Test
    void legacyViewConstructorKeepsPlayerDefaults() {
        var view = new CitizenBroadcastMemoryView("id", "Maria", "Hi", 1L, "Steve");
        assertEquals(MemoryProvenance.PLAYER_STATEMENT, view.provenance());
        assertNull(view.sourceLabel());
        assertEquals(0L, view.expiresAtMs());
    }

    private record Citizen(@NotNull UUID citizenId, @NotNull String name, @NotNull CitizenMemories memories)
            implements BroadcastPublisher.Recipient {
    }

    private static final class FakeColony implements BroadcastPublisher.Colony {
        final List<Citizen> citizens = new ArrayList<>();
        final List<Citizen> nearby = new ArrayList<>();
        private final Object key = new Object();

        FakeColony(String... names) {
            for (String name : names) citizens.add(new Citizen(UUID.randomUUID(), name, new CitizenMemories()));
        }

        @Override public @NotNull Object rateKey() { return key; }
        @Override public @NotNull List<Citizen> all() { return citizens; }
        @Override public @Nullable Citizen byId(@NotNull UUID id) {
            return citizens.stream().filter(c -> c.citizenId().equals(id)).findFirst().orElse(null);
        }
        @Override public @NotNull List<Citizen> near(@NotNull BlockPos position) { return nearby; }
    }

    private record SingleColony(CitizenMemories memories) implements BroadcastPublisher.Colony {
        @Override public @NotNull Object rateKey() { return this; }
        @Override public @NotNull List<Citizen> all() { return List.of(new Citizen(UUID.randomUUID(), "Solo", memories)); }
        @Override public @Nullable Citizen byId(@NotNull UUID id) { return null; }
        @Override public @NotNull List<Citizen> near(@NotNull BlockPos position) { return List.of(); }
    }
}
