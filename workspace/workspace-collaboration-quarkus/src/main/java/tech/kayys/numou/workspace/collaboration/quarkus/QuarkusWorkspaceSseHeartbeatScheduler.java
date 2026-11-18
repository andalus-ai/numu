package tech.kayys.numou.workspace.collaboration.quarkus;

import tech.kayys.numou.workspace.adapter.rest.WorkspaceSseHeartbeatScheduler;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * One shared daemon scheduler for all SSE connections in this process. Each connection owns only
 * a cancellable scheduled task; it does not allocate a thread.
 */
public final class QuarkusWorkspaceSseHeartbeatScheduler
        implements WorkspaceSseHeartbeatScheduler, AutoCloseable {
    private final ScheduledExecutorService executor;
    private final long interval;
    private final TimeUnit unit;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger activeStreams = new AtomicInteger();
    private final LongAdder heartbeatCallbacks = new LongAdder();
    private final LongAdder callbackFailures = new LongAdder();

    public QuarkusWorkspaceSseHeartbeatScheduler(long intervalSeconds) {
        this(intervalSeconds, TimeUnit.SECONDS);
        if (intervalSeconds < 5 || intervalSeconds > 300) {
            close();
            throw new IllegalArgumentException("heartbeat interval must be between 5 and 300 seconds");
        }
    }

    QuarkusWorkspaceSseHeartbeatScheduler(long interval, TimeUnit unit) {
        if (interval < 1) throw new IllegalArgumentException("heartbeat interval must be positive");
        this.interval = interval;
        this.unit = Objects.requireNonNull(unit, "unit");
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
        activeStreams.incrementAndGet();
        if (closed.get()) {
            activeStreams.decrementAndGet();
            throw new IllegalStateException("heartbeat scheduler is closed");
        }
        final ScheduledFuture<?> future;
        try {
            future = executor.scheduleAtFixedRate(() -> {
                if (closed.get()) return;
                heartbeatCallbacks.increment();
                try {
                    heartbeat.run();
                } catch (RuntimeException failure) {
                    callbackFailures.increment();
                    // One broken stream must not suppress heartbeats for every other connection.
                }
            }, interval, interval, unit);
        } catch (RuntimeException rejected) {
            activeStreams.decrementAndGet();
            throw rejected;
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        return () -> {
            if (cancelled.compareAndSet(false, true)) {
                future.cancel(false);
                activeStreams.decrementAndGet();
            }
        };
    }

    public HealthSnapshot health() {
        return new HealthSnapshot(activeStreams.get(), heartbeatCallbacks.sum(), callbackFailures.sum(), closed.get());
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
            activeStreams.set(0);
        }
    }

    public record HealthSnapshot(int activeStreams, long heartbeatCallbacks,
            long callbackFailures, boolean closed) {}
}
