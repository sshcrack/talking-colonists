package me.sshcrack.mc_talking.internal.provider;

import me.sshcrack.mc_talking.api.provider.ModelQuotaView;
import me.sshcrack.mc_talking.api.provider.ProviderBudgetView;
import me.sshcrack.mc_talking.api.provider.ProviderConfigView;
import me.sshcrack.mc_talking.api.provider.ProviderQuotaState;
import me.sshcrack.mc_talking.api.provider.SlotUsage;
import me.sshcrack.mc_talking.config.QuotaSnapshot;
import me.sshcrack.mc_talking.config.QuotaStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderBudgetRuntimeTest {
    private static final String LIVE = "live-model";
    private static final String TEXT = "text-model";

    private static final class FakeSources implements ProviderBudgetRuntime.Sources {
        SlotUsage foreground = new SlotUsage(1, 2);
        SlotUsage background = new SlotUsage(0, 1);
        boolean apiKey = true;
        final Map<String, QuotaSnapshot> quotas = new HashMap<>();
        long now = 1_000;

        @Override public SlotUsage foreground() { return foreground; }
        @Override public SlotUsage background() { return background; }
        @Override public ProviderConfigView config() { return new ProviderConfigView(apiKey, LIVE, TEXT, 3.0); }
        @Override public QuotaSnapshot modelQuota(String model) {
            return quotas.getOrDefault(model, new QuotaSnapshot(model, QuotaStatus.OK, now, null));
        }
        @Override public QuotaSnapshot ttsQuota() { return modelQuota("tts"); }
        @Override public long nowMs() { return now; }

        void exhaust(String model, Long resetAt) {
            quotas.put(model, new QuotaSnapshot(model, QuotaStatus.EXHAUSTED, now, resetAt));
        }
    }

    @Test
    void snapshotReportsSlotsAndEveryModel() {
        var sources = new FakeSources();
        sources.exhaust(TEXT, 61_000L);
        ProviderBudgetView view = new ProviderBudgetRuntime(sources).snapshot();

        assertEquals(1, view.foreground().available());
        assertEquals(1, view.background().available());
        assertEquals(List.of(LIVE, TEXT, "tts"), view.models().stream().map(ModelQuotaView::model).toList());
        ModelQuotaView text = view.model(TEXT).orElseThrow();
        assertEquals(ProviderQuotaState.EXHAUSTED, text.state());
        assertEquals(Instant.ofEpochMilli(61_000), text.exhaustedUntil());
        assertFalse(view.canStartConversation(), "an exhausted model blocks new conversations");
        assertEquals(Instant.ofEpochMilli(1_000), view.capturedAt());
    }

    @Test
    void withoutAnApiKeyEveryModelIsUnknown() {
        var sources = new FakeSources();
        sources.apiKey = false;
        var view = new ProviderBudgetRuntime(sources).snapshot();
        assertTrue(view.models().stream().allMatch(m -> m.state() == ProviderQuotaState.UNKNOWN));
    }

    @Test
    void listenersHearEachTransitionOnceOnTheNextPoll() {
        var sources = new FakeSources();
        var runtime = new ProviderBudgetRuntime(sources);
        List<String> heard = new ArrayList<>();
        runtime.registerQuotaListener("test:listener", 0,
                (previous, current) -> heard.add(current.model() + ":" + previous.state() + "->" + current.state()));

        runtime.poll();
        assertTrue(heard.isEmpty(), "the first poll only records states");

        sources.exhaust(LIVE, null);
        runtime.poll();
        runtime.poll();
        assertEquals(List.of(LIVE + ":OK->EXHAUSTED"), heard, "one event per change, not per poll");

        sources.quotas.remove(LIVE);
        runtime.poll();
        assertEquals(List.of(LIVE + ":OK->EXHAUSTED", LIVE + ":EXHAUSTED->OK"), heard);
    }

    @Test
    void aFailingListenerDoesNotStopTheOthers() {
        var sources = new FakeSources();
        var runtime = new ProviderBudgetRuntime(sources);
        List<String> heard = new ArrayList<>();
        runtime.registerQuotaListener("test:broken", 0, (previous, current) -> {
            throw new IllegalStateException("boom");
        });
        runtime.registerQuotaListener("test:working", 1, (previous, current) -> heard.add(current.model()));
        runtime.poll();
        sources.exhaust("tts", null);
        runtime.poll();
        assertEquals(List.of("tts"), heard);
    }

    @Test
    void viewsAreImmutable() {
        var view = new ProviderBudgetRuntime(new FakeSources()).snapshot();
        assertThrows(UnsupportedOperationException.class, () -> view.models().clear());

        var models = new ArrayList<ModelQuotaView>();
        models.add(new ModelQuotaView(LIVE, ProviderQuotaState.OK, null));
        var copy = new ProviderBudgetView(new SlotUsage(0, 1), new SlotUsage(0, 1), models, Instant.EPOCH);
        models.clear();
        assertEquals(1, copy.models().size(), "the view copies its model list");

        assertNull(new ModelQuotaView(LIVE, ProviderQuotaState.OK, Instant.EPOCH).exhaustedUntil(),
                "only an exhausted model carries a reset time");
        assertEquals(0, new SlotUsage(5, 2).available());
    }
}
