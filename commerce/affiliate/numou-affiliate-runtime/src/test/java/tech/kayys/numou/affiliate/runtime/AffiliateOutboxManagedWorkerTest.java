package tech.kayys.numou.affiliate.runtime;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.affiliate.application.AffiliateOutboxDispatcher;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;
import tech.kayys.numou.observability.MetricName;
import tech.kayys.numou.observability.MetricsPort;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateOutboxManagedWorkerTest {

    @Test
    void metricsFailureDoesNotWedgeDispatchGateAndStoreFailureIsRetried() throws Exception {
        FailingFirstClaimStore store = new FailingFirstClaimStore();
        MetricsPort brokenCounter = new MetricsPort() {
            @Override public void increment(MetricName name, long amount) {
                throw new IllegalStateException("metrics backend unavailable");
            }
            @Override public void record(MetricName name, Duration duration) {}
            @Override public void gauge(MetricName name, Supplier<Number> value) {}
        };
        AffiliateOutboxMetrics metrics = new AffiliateOutboxMetrics(brokenCounter);
        AffiliateOutboxDispatcher dispatcher = new AffiliateOutboxDispatcher(store,
                event -> CompletableFuture.completedFuture(null), Clock.systemUTC(),
                Duration.ofSeconds(10), Duration.ofMillis(10), Duration.ofSeconds(1), 10, 5);
        AffiliateOutboxManagedWorker worker = new AffiliateOutboxManagedWorker("tenant-a", "worker-a",
                dispatcher, store, metrics, Duration.ofMillis(20), Duration.ofSeconds(2));

        worker.start().await().atMost(Duration.ofSeconds(1));
        try {
            assertTrue(store.secondClaim.await(2, TimeUnit.SECONDS),
                    "worker should continue polling after metrics recording fails");
            assertTrue(store.healthReads.get() > 0,
                    "health refresh should still run after dispatch failure");
        } finally {
            worker.stop().await().atMost(Duration.ofSeconds(2));
        }
    }

    @Test
    void startIsIdempotentAndWorkerCanRestartAfterSuccessfulStop() {
        FailingFirstClaimStore store = new FailingFirstClaimStore();
        MetricsPort metricsPort = new MetricsPort() {
            @Override public void increment(MetricName name, long amount) {}
            @Override public void record(MetricName name, Duration duration) {}
            @Override public void gauge(MetricName name, Supplier<Number> value) {}
        };
        AffiliateOutboxMetrics metrics = new AffiliateOutboxMetrics(metricsPort);
        AffiliateOutboxDispatcher dispatcher = new AffiliateOutboxDispatcher(store,
                event -> CompletableFuture.completedFuture(null), Clock.systemUTC(),
                Duration.ofSeconds(10), Duration.ofMillis(10), Duration.ofSeconds(1), 10, 5);
        AffiliateOutboxManagedWorker worker = new AffiliateOutboxManagedWorker("tenant-a", "worker-b",
                dispatcher, store, metrics, Duration.ofMillis(25), Duration.ofSeconds(2));

        worker.start().await().atMost(Duration.ofSeconds(1));
        worker.start().await().atMost(Duration.ofSeconds(1));
        assertTrue(worker.isRunning());

        worker.stop().await().atMost(Duration.ofSeconds(2));
        assertFalse(worker.isRunning());

        worker.start().await().atMost(Duration.ofSeconds(1));
        assertTrue(worker.isRunning());
        worker.stop().await().atMost(Duration.ofSeconds(2));
        assertFalse(worker.isRunning());
    }

    private static final class FailingFirstClaimStore implements AffiliateEventOutboxStore {
        private final AtomicInteger claims = new AtomicInteger();
        private final AtomicInteger healthReads = new AtomicInteger();
        private final CountDownLatch secondClaim = new CountDownLatch(1);

        @Override
        public CompletionStage<List<ClaimedEvent>> claimPending(String tenantId, String workerId,
                Instant now, Duration lease, int limit) {
            if (claims.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("temporary store failure"));
            }
            secondClaim.countDown();
            return CompletableFuture.completedFuture(List.of());
        }

        @Override public CompletionStage<Void> markDelivered(String tenantId, java.util.UUID eventId,
                String workerId, long leaseVersion, Instant now) {
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<Void> reschedule(String tenantId, java.util.UUID eventId,
                String workerId, String reason, Instant retryAt, Instant now, int maxAttempts, long leaseVersion) {
            return CompletableFuture.completedFuture(null);
        }

        @Override public CompletionStage<Boolean> redriveDeadLetter(String tenantId, java.util.UUID eventId,
                String actorId, String reason, String idempotencyKey, String correlationId, Instant now) {
            return CompletableFuture.completedFuture(false);
        }

        @Override public CompletionStage<DeadLetterPage> findDeadLetters(String tenantId,
                DeadLetterCursor before, int limit) {
            return CompletableFuture.completedFuture(new DeadLetterPage(List.of(), null));
        }

        @Override public CompletionStage<OutboxHealthSnapshot> healthSnapshot() {
            healthReads.incrementAndGet();
            return CompletableFuture.completedFuture(new OutboxHealthSnapshot(0, 0, 0, 0, 0, 0));
        }
    }
}
