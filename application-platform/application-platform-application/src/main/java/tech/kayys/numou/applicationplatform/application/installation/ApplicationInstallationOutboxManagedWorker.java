package tech.kayys.numou.applicationplatform.application.installation;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.runtime.ManagedWorker;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;

/**
 * One worker instance per tenant. The database lease coordinates replicas;
 * this scheduler prevents overlapping local polls and drains on shutdown.
 */
public final class ApplicationInstallationOutboxManagedWorker implements ManagedWorker {
    private final TenantId tenantId;
    private final String workerId;
    private final ApplicationInstallationOutboxDispatcher dispatcher;
    private final Duration interval;
    private final Duration stopTimeout;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private volatile ScheduledExecutorService executor;
    private final AtomicReference<Instant> lastCompletedAt = new AtomicReference<>();
    private final AtomicReference<String> lastFailureCode = new AtomicReference<>();

    public ApplicationInstallationOutboxManagedWorker(TenantId tenantId, String workerId,
            ApplicationInstallationOutboxDispatcher dispatcher, Duration interval, Duration stopTimeout) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.workerId = required(workerId, "workerId");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.interval = positive(interval, "interval");
        this.stopTimeout = positive(stopTimeout, "stopTimeout");
    }

    @Override public String id() { return "application-installation:" + tenantId + ":" + workerId; }

    @Override public synchronized Uni<Void> start() {
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
        try {
            dispatcher.dispatchOnce(tenantId, workerId).whenComplete((ignored, failure) -> {
                lastCompletedAt.set(Instant.now());
                if (failure == null) lastFailureCode.set(null);
                else lastFailureCode.set("WORKER_DISPATCH_FAILURE");
                inFlight.set(false);
            });
        } catch (Throwable failure) {
            lastCompletedAt.set(Instant.now());
            lastFailureCode.set("WORKER_DISPATCH_FAILURE");
            inFlight.set(false);
        }
    }

    @Override public synchronized Uni<Void> stop() {
        ScheduledExecutorService stopping = executor;
        if (stopping == null) return Uni.createFrom().voidItem();
        stopping.shutdown();
        return Uni.createFrom().item(() -> {
            long deadline = System.nanoTime() + stopTimeout.toNanos();
            try {
                while (inFlight.get() && System.nanoTime() < deadline) Thread.sleep(20);
                long remaining = Math.max(0, deadline - System.nanoTime());
                if (!stopping.awaitTermination(remaining, TimeUnit.NANOSECONDS)) stopping.shutdownNow();
                if (inFlight.get()) throw new IllegalStateException("Application installation worker did not drain before stop timeout");
                return (Void) null;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                stopping.shutdownNow();
                throw new IllegalStateException("Interrupted while stopping application installation worker", interrupted);
            }
        });
    }

    public Instant lastCompletedAt() { return lastCompletedAt.get(); }

    public String lastFailureCode() { return lastFailureCode.get(); }

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
