package me.sshcrack.mc_talking.internal.session;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Bounded server dispatch. Timed-out queued work cannot mutate the world later. */
public final class ServerThreadGate {
    private final Executor executor;
    private final BooleanSupplier onServerThread;

    public ServerThreadGate(Executor executor, BooleanSupplier onServerThread) {
        this.executor = executor;
        this.onServerThread = onServerThread;
    }

    public <T> T call(BooleanSupplier available, Supplier<T> action, long timeoutMillis) throws Exception {
        Supplier<T> checked = () -> {
            if (!available.getAsBoolean()) throw new CancellationException("Owning conversation is no longer active");
            return action.get();
        };
        if (onServerThread.getAsBoolean()) return checked.get();
        FutureTask<T> task = new FutureTask<>(checked::get);
        executor.execute(task);
        try { return task.get(timeoutMillis, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
        finally { if (!task.isDone()) task.cancel(false); }
    }
}
