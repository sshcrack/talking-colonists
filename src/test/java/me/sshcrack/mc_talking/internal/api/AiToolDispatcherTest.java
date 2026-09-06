package me.sshcrack.mc_talking.internal.api;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.tool.AiCommandTool;
import me.sshcrack.mc_talking.api.tool.AiQueryTool;
import me.sshcrack.mc_talking.api.tool.AiToolContext;
import me.sshcrack.mc_talking.api.tool.AiToolOperationOutcome;
import me.sshcrack.mc_talking.api.tool.AiToolParameter;
import me.sshcrack.mc_talking.api.tool.AiToolPermission;
import me.sshcrack.mc_talking.api.tool.AiToolScope;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiToolDispatcherTest {
    @Test
    void rejectsDisabledMalformedAndWrongSessionCallsBeforeExecution() {
        AtomicInteger executions = new AtomicInteger();
        AiQueryTool tool = new AiQueryTool() {
            @Override
            public String description() {
                return "lookup";
            }

            @Override
            public AiToolParameter parameters() {
                return AiToolParameter.object(Map.of("count", AiToolParameter.integer(true)));
            }

            @Override
            public JsonObject executeQuery(AiToolContext context, JsonObject parameters) {
                executions.incrementAndGet();
                return json("ok", true);
            }
        };
        var registered = registered("lookup", tool);
        AtomicBoolean enabled = new AtomicBoolean(false);
        AiToolDispatcher dispatcher = dispatcher(registered, enabled::get, (context, permission) -> true);
        FakeContext context = new FakeContext(UUID.randomUUID(), UUID.randomUUID());
        FakeSession session = new FakeSession(context.sessionId(), context);

        assertError(dispatcher.dispatch("call-1", registered.providerName(), json("count", 1), session), "disabled");
        enabled.set(true);
        assertError(dispatcher.dispatch("call-2", registered.providerName(), json("count", "one"), session), "invalid_arguments");
        assertError(dispatcher.dispatch("call-3", registered.providerName(), json("count", 1, "player", "spoof"), session), "invalid_arguments");

        FakeSession wrongSession = new FakeSession(UUID.randomUUID(), context);
        assertError(dispatcher.dispatch("call-4", registered.providerName(), json("count", 1), wrongSession), "wrong_session");
        assertEquals(0, executions.get());
    }

    @Test
    void rechecksCurrentPermissionAndUsesAuthenticatedActorInsteadOfModelData() {
        AtomicBoolean allowed = new AtomicBoolean(true);
        AtomicInteger executions = new AtomicInteger();
        UUID authenticated = UUID.randomUUID();
        UUID spoofed = UUID.randomUUID();
        AiQueryTool tool = new AiQueryTool() {
            @Override
            public String description() {
                return "authorized lookup";
            }

            @Override
            public AiToolScope scope() {
                return AiToolScope.PLAYER_CONVERSATION;
            }

            @Override
            public AiToolPermission permission() {
                return AiToolPermission.EDIT_PERMISSIONS;
            }

            @Override
            public AiToolParameter parameters() {
                return AiToolParameter.object(Map.of("claimedPlayer", AiToolParameter.string(true)));
            }

            @Override
            public JsonObject executeQuery(AiToolContext context, JsonObject parameters) {
                executions.incrementAndGet();
                return json("actor", context.authenticatedPlayerId().toString());
            }
        };
        var registered = registered("authorized", tool);
        AiToolDispatcher dispatcher = dispatcher(registered, () -> true, (context, permission) -> allowed.get());
        FakeContext context = new FakeContext(UUID.randomUUID(), authenticated);
        FakeSession session = new FakeSession(context.sessionId(), context);

        // The tool could have been advertised while this was true. Execution must use current state.
        allowed.set(false);
        assertError(dispatcher.dispatch("call-1", registered.providerName(),
                json("claimedPlayer", spoofed.toString()), session), "unauthorized");
        assertEquals(0, executions.get());

        allowed.set(true);
        JsonObject response = dispatcher.dispatch("call-2", registered.providerName(),
                json("claimedPlayer", spoofed.toString()), session);
        assertEquals("completed", response.get("status").getAsString());
        assertEquals(authenticated.toString(), response.getAsJsonObject("result").get("actor").getAsString());
        assertNotEquals(spoofed.toString(), response.getAsJsonObject("result").get("actor").getAsString());
        assertEquals(1, executions.get());
        assertTrue(context.serverThreadUsed.get());
    }

    @Test
    void commandCallIdsAreIdempotentButDistinctCallsRemainUsable() {
        AtomicInteger sideEffects = new AtomicInteger();
        List<CompletableFuture<JsonObject>> completions = new ArrayList<>();
        AiCommandTool command = new AiCommandTool() {
            @Override
            public String description() {
                return "delayed command";
            }

            @Override
            public AiToolParameter parameters() {
                return AiToolParameter.object(Map.of("target", AiToolParameter.string(true)));
            }

            @Override
            public CompletionStage<JsonObject> executeCommand(AiToolContext context, JsonObject parameters) {
                sideEffects.incrementAndGet();
                CompletableFuture<JsonObject> future = new CompletableFuture<>();
                completions.add(future);
                return future;
            }
        };
        var registered = registered("command", command);
        AiToolDispatcher dispatcher = dispatcher(registered, () -> true, (context, permission) -> true);
        FakeContext context = new FakeContext(UUID.randomUUID(), UUID.randomUUID());
        FakeSession session = new FakeSession(context.sessionId(), context);

        JsonObject first = dispatcher.dispatch("same-call", registered.providerName(), json("target", "townhall"), session);
        JsonObject duplicate = dispatcher.dispatch("same-call", registered.providerName(), json("target", "townhall"), session);
        assertEquals("accepted", first.get("status").getAsString());
        assertEquals(first.get("operationId").getAsString(), duplicate.get("operationId").getAsString());
        assertEquals(1, sideEffects.get());
        assertEquals(1, dispatcher.activeCommandCount());
        assertTrue(context.serverThreadUsed.get());

        assertError(dispatcher.dispatch("same-call", registered.providerName(), json("target", "warehouse"), session),
                "call_id_conflict");
        assertEquals(1, sideEffects.get());

        JsonObject distinct = dispatcher.dispatch("different-call", registered.providerName(), json("target", "warehouse"), session);
        assertNotEquals(first.get("operationId").getAsString(), distinct.get("operationId").getAsString());
        assertEquals(2, sideEffects.get());

        completions.get(0).complete(json("moved", true));
        JsonObject terminalDuplicate = dispatcher.dispatch("same-call", registered.providerName(), json("target", "townhall"), session);
        assertEquals("completed", terminalDuplicate.get("status").getAsString());
        assertEquals(2, sideEffects.get());
    }

    @Test
    void completionAfterSessionClosureDoesNotDeliverOrRetainAndStillNotifiesAddon() {
        CompletableFuture<JsonObject> completion = new CompletableFuture<>();
        AtomicReference<AiToolOperationOutcome> observed = new AtomicReference<>();
        AtomicInteger starts = new AtomicInteger();
        AiCommandTool command = new AiCommandTool() {
            @Override
            public String description() {
                return "delayed command";
            }

            @Override
            public CompletionStage<JsonObject> executeCommand(AiToolContext context, JsonObject parameters) {
                starts.incrementAndGet();
                return completion;
            }

            @Override
            public void onCompletion(AiToolOperationOutcome outcome) {
                observed.set(outcome);
            }
        };
        var registered = registered("late", command);
        AiToolDispatcher dispatcher = dispatcher(registered, () -> true, (context, permission) -> true);
        FakeContext context = new FakeContext(UUID.randomUUID(), UUID.randomUUID());
        FakeSession session = new FakeSession(context.sessionId(), context);

        JsonObject accepted = dispatcher.dispatch("late-call", registered.providerName(), null, session);
        assertEquals("accepted", accepted.get("status").getAsString());
        session.available.set(false);
        completion.complete(json("done", true));

        assertEquals(1, starts.get());
        assertTrue(session.deliveries.isEmpty());
        assertEquals(0, dispatcher.activeCommandCount());
        assertEquals(0, dispatcher.retainedTerminalCount());
        assertEquals("late-call", observed.get().callId());
        assertEquals("COMPLETED", observed.get().status().name());
        assertFalse(observed.get().deliveredToSession());
    }

    @Test
    void cancelledCommandsAndTerminalRetentionAreBounded() {
        List<CompletableFuture<JsonObject>> completions = new ArrayList<>();
        AiCommandTool command = new AiCommandTool() {
            @Override
            public String description() {
                return "command";
            }

            @Override
            public CompletionStage<JsonObject> executeCommand(AiToolContext context, JsonObject parameters) {
                CompletableFuture<JsonObject> future = new CompletableFuture<>();
                completions.add(future);
                return future;
            }
        };
        var registered = registered("bounded", command);
        AiToolDispatcher dispatcher = new AiToolDispatcher(
                4,
                2,
                providerName -> registered.providerName().equals(providerName) ? registered : null,
                ignored -> true,
                (context, permission) -> true
        );
        FakeContext context = new FakeContext(UUID.randomUUID(), UUID.randomUUID());
        FakeSession session = new FakeSession(context.sessionId(), context);

        for (int i = 0; i < 3; i++) {
            dispatcher.dispatch("call-" + i, registered.providerName(), null, session);
            if (i == 0) completions.get(i).cancel(false);
            else completions.get(i).complete(json("index", i));
        }

        assertEquals(0, dispatcher.activeCommandCount());
        assertEquals(2, dispatcher.retainedTerminalCount());
        assertTrue(session.deliveries.stream().anyMatch(result -> "cancelled".equals(result.get("status").getAsString())));
    }

    @Test
    void commandCapacityRejectsWithoutStartingExtraSideEffects() {
        AtomicInteger starts = new AtomicInteger();
        AiCommandTool command = new AiCommandTool() {
            @Override
            public String description() {
                return "capacity";
            }

            @Override
            public CompletionStage<JsonObject> executeCommand(AiToolContext context, JsonObject parameters) {
                starts.incrementAndGet();
                return new CompletableFuture<>();
            }
        };
        var registered = registered("capacity", command);
        AiToolDispatcher dispatcher = new AiToolDispatcher(
                1,
                1,
                providerName -> registered,
                ignored -> true,
                (context, permission) -> true
        );
        FakeContext context = new FakeContext(UUID.randomUUID(), UUID.randomUUID());
        FakeSession session = new FakeSession(context.sessionId(), context);

        dispatcher.dispatch("first", registered.providerName(), null, session);
        assertError(dispatcher.dispatch("second", registered.providerName(), null, session), "command_capacity");
        assertEquals(1, starts.get());
    }

    private static AiToolDispatcher dispatcher(
            AiToolRuntime.RegisteredTool registered,
            Supplier<Boolean> enabled,
            AiToolDispatcher.PermissionChecker permissionChecker
    ) {
        return new AiToolDispatcher(
                8,
                8,
                providerName -> registered.providerName().equals(providerName) ? registered : null,
                ignored -> enabled.get(),
                permissionChecker
        );
    }

    private static AiToolRuntime.RegisteredTool registered(String name, me.sshcrack.mc_talking.api.tool.AiTool tool) {
        return new AiToolRuntime.RegisteredTool("test:" + name, "tc_test_" + name, tool);
    }

    private static void assertError(JsonObject response, String code) {
        assertEquals("failed", response.get("status").getAsString(), response::toString);
        assertEquals(code, response.getAsJsonObject("error").get("code").getAsString(), response::toString);
    }

    private static JsonObject json(Object... values) {
        JsonObject object = new JsonObject();
        for (int i = 0; i < values.length; i += 2) {
            String key = (String) values[i];
            Object value = values[i + 1];
            if (value instanceof Boolean bool) object.addProperty(key, bool);
            else if (value instanceof Number number) object.addProperty(key, number);
            else object.addProperty(key, String.valueOf(value));
        }
        return object;
    }

    private static final class FakeSession implements AiToolDispatcher.SessionEndpoint {
        private final UUID sessionId;
        private final FakeContext context;
        private final AtomicBoolean available = new AtomicBoolean(true);
        private final List<JsonObject> deliveries = new ArrayList<>();

        private FakeSession(UUID sessionId, FakeContext context) {
            this.sessionId = sessionId;
            this.context = context;
        }

        @Override
        public @NotNull UUID sessionId() {
            return sessionId;
        }

        @Override
        public @NotNull AiToolContext context() {
            return context;
        }

        @Override
        public boolean isAvailable() {
            return available.get();
        }

        @Override
        public boolean deliver(@NotNull JsonObject outcome) {
            if (!available.get()) return false;
            deliveries.add(outcome.deepCopy());
            return true;
        }
    }

    private static final class FakeContext implements AiToolContext {
        private final UUID sessionId;
        @Nullable private final UUID playerId;
        private final AtomicBoolean serverThreadUsed = new AtomicBoolean(false);

        private FakeContext(UUID sessionId, @Nullable UUID playerId) {
            this.sessionId = sessionId;
            this.playerId = playerId;
        }

        @Override
        public @NotNull UUID sessionId() {
            return sessionId;
        }

        @Override
        public @NotNull AbstractEntityCitizen citizen() {
            throw new UnsupportedOperationException("not needed by dispatcher unit tests");
        }

        @Override
        public @NotNull IColony colony() {
            throw new UnsupportedOperationException("permission checker is injected by tests");
        }

        @Override
        public @Nullable ServerPlayer player() {
            return null;
        }

        @Override
        public @Nullable UUID authenticatedPlayerId() {
            return playerId;
        }

        @Override
        public <T> @NotNull CompletableFuture<T> supplyOnServerThread(@NotNull Supplier<T> action) {
            serverThreadUsed.set(true);
            try {
                return CompletableFuture.completedFuture(action.get());
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
        }
    }
}
