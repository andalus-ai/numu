package tech.kayys.numou.affiliate.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.affiliate.application.AffiliateOutboxDispatcher;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;
import tech.kayys.numou.foundation.application.runtime.ManagedWorker;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single-tenant managed worker. Hosts create one instance per authorized tenant
 * (or tenant shard); PostgreSQL leases coordinate competing instances safely.
 */
public final class AffiliateOutboxManagedWorker implements ManagedWorker {
    private final String tenantId;
    private final String workerId;
    private final AffiliateOutboxDispatcher dispatcher;
    private final AffiliateEventOutboxStore store;
    private final AffiliateOutboxMetrics metrics;
    private final Duration interval;
    private final Duration stopTimeout;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private ScheduledExecutorService executor;

    public AffiliateOutboxManagedWorker(String tenantId, String workerId,
            AffiliateOutboxDispatcher dispatcher, AffiliateEventOutboxStore store,
            AffiliateOutboxMetrics metrics, Duration interval, Duration stopTimeout) {
        this.tenantId = required(tenantId, "tenantId");
        this.workerId = required(workerId, "workerId");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.store = Objects.requireNonNull(store, "store");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.interval = positive(interval, "interval");
        this.stopTimeout = positive(stopTimeout, "stopTimeout");
    }

    @Override public String id() { return "affiliate-outbox:" + tenantId + ":" + workerId; }

    @Override
    public synchronized Uni<Void> start() {
        if (executor != null && !executor.isShutdown()) return Uni.createFrom().voidItem();
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, id());
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        return Uni.createFrom().voidItem();
    }

    private void tick() {
        if (!inFlight.compareAndSet(false, true)) return;
        try {
            CompletionStage<Integer> dispatch = Objects.requireNonNull(
                    dispatcher.dispatchOnce(tenantId, workerId), "dispatcher returned null stage");
            dispatch.whenComplete((delivered, failure) -> {
                try {
                    if (failure != null) recordFailureSafely();
                    else recordDeliveredSafely(delivered == null ? 0 : delivered);
                } finally {
                    // Telemetry must never own worker liveness. Always attempt the
                    // health refresh and release the gate even if a metrics adapter fails.
                    refreshHealth();
                }
            });
        } catch (Throwable failure) {
            recordFailureSafely();
            refreshHealth();
        }
    }

    private void refreshHealth() {
        try {
            CompletionStage<AffiliateEventOutboxStore.OutboxHealthSnapshot> health =
                    Objects.requireNonNull(store.healthSnapshot(), "healthSnapshot returned null stage");
            health.whenComplete((snapshot, failure) -> {
                try {
                    if (failure != null) recordFailureSafely();
                    else if (snapshot != null) {
                        try {
                            metrics.refresh(snapshot);
                        } catch (Throwable ignored) {
                            // A broken metrics sink must not wedge dispatch scheduling.
                        }
                    }
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (Throwable failure) {
            recordFailureSafely();
            inFlight.set(false);
        }
    }

    private void recordFailureSafely() {
        try {
            metrics.recordDispatchFailure();
        } catch (Throwable ignored) {
            // Metrics are best-effort and cannot control delivery lifecycle.
        }
    }

    private void recordDeliveredSafely(long delivered) {
        try {
            metrics.recordDelivered(delivered);
        } catch (Throwable ignored) {
            // Metrics are best-effort and cannot control delivery lifecycle.
        }
    }

    @Override
    public synchronized Uni<Void> stop() {
        if (executor == null) return Uni.createFrom().voidItem();
        ScheduledExecutorService stopping = executor;
        stopping.shutdown();
        return Uni.createFrom().item(() -> {
            try {
                long deadline = System.nanoTime() + stopTimeout.toNanos();
                while (inFlight.get() && System.nanoTime() < deadline) Thread.sleep(25);
                long remaining = Math.max(0, deadline - System.nanoTime());
                if (!stopping.awaitTermination(remaining, TimeUnit.NANOSECONDS)) stopping.shutdownNow();
                if (inFlight.get()) throw new IllegalStateException(
                        "Affiliate outbox worker did not drain before stop timeout");
                return (Void) null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopping.shutdownNow();
                throw new IllegalStateException("Interrupted while stopping Affiliate outbox worker", e);
            }
        });
    }

    @Override public synchronized boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative() || value.toMillis() < 1)
            throw new IllegalArgumentException(name + " must be at least 1 millisecond");
        return value;
    }
}
