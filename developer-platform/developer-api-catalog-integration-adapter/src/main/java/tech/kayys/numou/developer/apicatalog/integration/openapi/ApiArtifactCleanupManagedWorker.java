package tech.kayys.numou.developer.apicatalog.integration.openapi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.developer.apicatalog.application.cleanup.ApiArtifactCleanupDispatcher;
import tech.kayys.numou.foundation.application.runtime.ManagedWorker;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * One managed worker per authorized provider tenant. The database lease, not this
 * local scheduler, provides cross-replica coordination.
 */
public final class ApiArtifactCleanupManagedWorker implements ManagedWorker {
    private final TenantId tenant;
    private final String workerId;
    private final ApiArtifactCleanupDispatcher dispatcher;
    private final DomainClock clock;
    private final Duration interval;
    private final Duration leaseDuration;
    private final Duration stopTimeout;
    private final int batchSize;
    private final int maximumAttempts;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicReference<Instant> lastCompletedAt = new AtomicReference<>();
    private final AtomicReference<String> lastFailureCode = new AtomicReference<>();
    private final LongAdder deletedCount = new LongAdder();
    private volatile ScheduledExecutorService executor;

    public ApiArtifactCleanupManagedWorker(TenantId tenant, String workerId,
            ApiArtifactCleanupDispatcher dispatcher, DomainClock clock,
            Duration interval, Duration leaseDuration, Duration stopTimeout,
            int batchSize, int maximumAttempts) {
        this.tenant = Objects.requireNonNull(tenant, "tenant cannot be null");
        this.workerId = required(workerId, "workerId");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.interval = positive(interval, "interval");
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.stopTimeout = positive(stopTimeout, "stopTimeout");
        if (batchSize < 1 || batchSize > 200 || maximumAttempts < 1) {
            throw new IllegalArgumentException("Invalid API artifact cleanup worker limits");
        }
        this.batchSize = batchSize;
        this.maximumAttempts = maximumAttempts;
    }

    @Override public String id() { return "developer-api-artifact-cleanup:" + tenant + ":" + workerId; }

    @Override
    public synchronized Uni<Void> start() {
        if (executor != null && !executor.isShutdown()) return Uni.createFrom().voidItem();
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, id());
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        return Uni.createFrom().voidItem();
    }

    private void tick() {
        if (!inFlight.compareAndSet(false, true)) return;
        Instant now = clock.now();
        try {
            dispatcher.dispatchOnce(tenant, workerId, now, leaseDuration, batchSize, maximumAttempts)
                    .subscribe().with(deleted -> {
                        deletedCount.add(deleted);
                        lastCompletedAt.set(clock.now());
                        lastFailureCode.set(null);
                        inFlight.set(false);
                    }, failure -> {
                        lastCompletedAt.set(clock.now());
                        lastFailureCode.set(failure.getClass().getSimpleName());
                        inFlight.set(false);
                    });
        } catch (Throwable failure) {
            lastCompletedAt.set(clock.now());
            lastFailureCode.set(failure.getClass().getSimpleName());
            inFlight.set(false);
        }
    }

    @Override
    public synchronized Uni<Void> stop() {
        ScheduledExecutorService stopping = executor;
        if (stopping == null) return Uni.createFrom().voidItem();
        stopping.shutdown();
        return Uni.createFrom().item(() -> {
            long deadline = System.nanoTime() + stopTimeout.toNanos();
            try {
                while (inFlight.get() && System.nanoTime() < deadline) Thread.sleep(20);
                long remaining = Math.max(0, deadline - System.nanoTime());
                if (!stopping.awaitTermination(remaining, TimeUnit.NANOSECONDS)) stopping.shutdownNow();
                if (inFlight.get()) throw new IllegalStateException("API artifact cleanup worker did not drain before stop timeout");
                return (Void) null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                stopping.shutdownNow();
                throw new IllegalStateException("Interrupted while stopping API artifact cleanup worker", interrupted);
            }
        });
    }

    public Instant lastCompletedAt() { return lastCompletedAt.get(); }
    public String lastFailureCode() { return lastFailureCode.get(); }
    public long deletedCount() { return deletedCount.sum(); }

    @Override public synchronized boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || value.trim().length() > 160)
            throw new IllegalArgumentException(name + " must contain 1 to 160 characters");
        return value.trim();
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative() || value.toMillis() < 1)
            throw new IllegalArgumentException(name + " must be at least one millisecond");
        return value;
    }
}
