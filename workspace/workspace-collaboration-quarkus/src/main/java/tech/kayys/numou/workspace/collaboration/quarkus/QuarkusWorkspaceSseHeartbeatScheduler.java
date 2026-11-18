package tech.kayys.numou.workspace.collaboration.quarkus;

import tech.kayys.numou.workspace.adapter.rest.WorkspaceSseHeartbeatScheduler;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One shared daemon scheduler for all SSE connections in this process. Each connection owns only
 * a cancellable scheduled task; it does not allocate a thread.
 */
public final class QuarkusWorkspaceSseHeartbeatScheduler
        implements WorkspaceSseHeartbeatScheduler, AutoCloseable {
    private final ScheduledExecutorService executor;
    private final long intervalSeconds;
    private final AtomicBoolean closed = new AtomicBoolean();

    public QuarkusWorkspaceSseHeartbeatScheduler(long intervalSeconds) {
        if (intervalSeconds < 5 || intervalSeconds > 300) {
            throw new IllegalArgumentException("heartbeat interval must be between 5 and 300 seconds");
        }
        this.intervalSeconds = intervalSeconds;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "numou-workspace-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public AutoCloseable schedule(Runnable heartbeat) {
        Objects.requireNonNull(heartbeat, "heartbeat");
        if (closed.get()) throw new IllegalStateException("heartbeat scheduler is closed");
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            try {
                heartbeat.run();
            } catch (RuntimeException ignored) {
                // The transport callback owns connection closure and telemetry. Keep one broken
                // stream from suppressing heartbeats for every other connection.
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        return () -> future.cancel(false);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) executor.shutdownNow();
    }
}
