package tech.kayys.numou.affiliate.runtime;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.application.AffiliateOutboxDispatcher;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.*;
import tech.kayys.numou.affiliate.spi.AffiliateEventPublisher;
import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateOutboxManagedWorkerTest {
    @Test
    void telemetryFailureDoesNotStrandInFlightGateOrStopFutureTicks() throws Exception {
        FailingStore store = new FailingStore();
        MetricsPort brokenMetrics = new MetricsPort() {
            @Override public void increment(MetricName name, long amount) {
                throw new IllegalStateException("metrics backend unavailable");
            }
            @Override public void record(MetricName name, Duration duration) {}
            @Override public void gauge(MetricName name, java.util.function.Supplier<Number> value) {}
        };
        AffiliateOutboxMetrics metrics = new AffiliateOutboxMetrics(brokenMetrics);
        AffiliateOutboxDispatcher dispatcher = new AffiliateOutboxDispatcher(store,
                event -> CompletableFuture.completedFuture(null), Clock.systemUTC(),
                Duration.ofSeconds(30), Duration.ofMillis(10), Duration.ofSeconds(1), 10, 3);
        AffiliateOutboxManagedWorker worker = new AffiliateOutboxManagedWorker(
                "tenant-a", "worker-a", dispatcher, store, metrics,
                Duration.ofMillis(10), Duration.ofSeconds(2));

        worker.start().await().atMost(Duration.ofSeconds(1));
        assertTrue(store.healthRefreshes.await(2, TimeUnit.SECONDS),
                "health refresh should continue even when failure metrics throw");
        worker.stop().await().atMost(Duration.ofSeconds(2));

        assertTrue(store.claims.get() >= 2, "worker should continue scheduling later dispatch passes");
        assertTrue(store.healthSnapshots.get() >= 2, "every failed dispatch should still refresh health");
        assertFalse(worker.isRunning());
    }

    @Test
    void startIsIdempotentAndStopIsSafeWhenNoPassIsInFlight() {
        FailingStore store = new FailingStore();
        MetricsPort metricsPort = new MetricsPort() {
            @Override public void increment(MetricName name, long amount) {}
            @Override public void record(MetricName name, Duration duration) {}
            @Override public void gauge(MetricName name, java.util.function.Supplier<Number> value) {}
        };
        AffiliateOutboxManagedWorker worker = new AffiliateOutboxManagedWorker(
                "tenant-a", "worker-a",
                new AffiliateOutboxDispatcher(store, event -> CompletableFuture.completedFuture(null),
                        Clock.systemUTC(), Duration.ofSeconds(30), Duration.ofMillis(10),
                        Duration.ofSeconds(1), 10, 3),
                store, new AffiliateOutboxMetrics(metricsPort),
                Duration.ofSeconds(10), Duration.ofSeconds(1));

        worker.start().await().atMost(Duration.ofSeconds(1));
        worker.start().await().atMost(Duration.ofSeconds(1));
        assertTrue(worker.isRunning());
        worker.stop().await().atMost(Duration.ofSeconds(2));
        worker.stop().await().atMost(Duration.ofSeconds(2));
        assertFalse(worker.isRunning());
    }

    private static final class FailingStore implements AffiliateEventOutboxStore {
        private final AtomicInteger claims = new AtomicInteger();
        private final AtomicInteger healthSnapshots = new AtomicInteger();
        private final CountDownLatch healthRefreshes = new CountDownLatch(2);

        @Override public CompletionStage<List<ClaimedEvent>> claimPending(
                String tenantId, String workerId, Instant now, Duration lease, int limit) {
            claims.incrementAndGet();
            return CompletableFuture.failedFuture(new IllegalStateException("database temporarily unavailable"));
        }
        @Override public CompletionStage<Void> markDelivered(String tenantId, UUID eventId, String workerId, Instant now) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<Void> reschedule(String tenantId, UUID eventId, String workerId,
                String reason, Instant retryAt, Instant now, int maxAttempts) {
            return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<Boolean> redriveDeadLetter(String tenantId, UUID eventId, String actorId,
                String reason, String idempotencyKey, String correlationId, Instant now) {
            return CompletableFuture.completedFuture(false);
        }
        @Override public CompletionStage<DeadLetterPage> findDeadLetters(String tenantId, DeadLetterCursor before, int limit) {
            return CompletableFuture.completedFuture(new DeadLetterPage(List.of(), null));
        }
        @Override public CompletionStage<OutboxHealthSnapshot> healthSnapshot() {
            healthSnapshots.incrementAndGet();
            healthRefreshes.countDown();
            return CompletableFuture.completedFuture(new OutboxHealthSnapshot(0, 0, 0, 0, 0, 0));
        }
    }
}
