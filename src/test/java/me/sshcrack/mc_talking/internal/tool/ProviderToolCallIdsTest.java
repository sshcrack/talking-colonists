package me.sshcrack.mc_talking.internal.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderToolCallIdsTest {
    private static final String BATCH = """
            {"toolCall": {"functionCalls": [
              {"name": "get_inventory", "id": "call-1", "args": {}},
              {"name": "example:come_here", "id": "call-2", "args": {"destination": "home"}}
            ]}}""";

    private final ProviderToolCallIds ids = new ProviderToolCallIds();
    private final List<String> mismatches = new ArrayList<>();

    @Test
    void batchedCallsGetTheirOwnIdsInOrder() {
        List<String> seen = new ArrayList<>();
        ids.handle(BATCH, () -> {
            seen.add(ids.poll("get_inventory", this::mismatch));
            seen.add(ids.poll("example:come_here", this::mismatch));
            seen.add(ids.poll("example:come_here", this::mismatch));
        });
        assertEquals(List.of("call-1", "call-2", ""), seen);
        assertTrue(mismatches.isEmpty());
    }

    @Test
    void idsAreOnlyAvailableWhileTheMessageIsHandled() {
        ids.handle(BATCH, () -> { });
        assertEquals("", ids.poll("get_inventory", this::mismatch));

        List<String> seen = new ArrayList<>();
        ids.handle("{\"serverContent\": {\"turnComplete\": true}}", () -> seen.add(ids.poll("get_inventory", this::mismatch)));
        assertEquals(List.of(""), seen);
    }

    @Test
    void aNameMismatchGivesNoIdAndIsReported() {
        List<String> seen = new ArrayList<>();
        ids.handle(BATCH, () -> seen.add(ids.poll("example:come_here", this::mismatch)));
        assertEquals(List.of(""), seen);
        assertEquals(List.of("get_inventory->example:come_here"), mismatches);
    }

    @Test
    void malformedOrPartialMessagesParseToWhatIsValid() {
        assertTrue(ProviderToolCallIds.parse("not json").isEmpty());
        assertTrue(ProviderToolCallIds.parse("[1, 2]").isEmpty());
        assertTrue(ProviderToolCallIds.parse("{\"toolCall\": {\"functionCalls\": 3}}").isEmpty());

        var calls = ProviderToolCallIds.parse("""
                {"toolCall": {"functionCalls": [{"id": "no-name"}, {"name": "a"}, 7, {"name": "b", "id": "x"}]}}""");
        assertEquals(List.of(new ProviderToolCallIds.Call("a", ""), new ProviderToolCallIds.Call("b", "x")),
                List.copyOf(calls));
    }

    @Test
    void handlerExceptionsStillClearTheIds() {
        try {
            ids.handle(BATCH, () -> { throw new IllegalStateException("library failure"); });
        } catch (IllegalStateException expected) {
            // The library's exception propagates unchanged.
        }
        assertEquals("", ids.poll("get_inventory", this::mismatch));
    }

    private void mismatch(String expected, String received) {
        mismatches.add(expected + "->" + received);
    }
}
