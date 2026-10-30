package tech.kayys.numou.queuemanagement.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.queuemanagement.spi.QueueEventPublisher;
import tech.kayys.numou.queuemanagement.spi.QueueOutboxDeliveryPort;
import tech.kayys.numou.queuemanagement.spi.QueueOutboxMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueOutboxDispatchServiceTest {
    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void publishesThenAcknowledgesUsingTheClaimedFencingToken() {
        UUID token = UUID.randomUUID();
        FakeDelivery delivery = new FakeDelivery(message(token, 1));
        AtomicReference<QueueOutboxMessage> published = new AtomicReference<>();
        QueueOutboxDispatchService service = new QueueOutboxDispatchService(delivery, published::set);

        var report = service.dispatchOnce(TENANT, "worker-a", 10, Duration.ofSeconds(30),
                5, Duration.ofSeconds(2), Duration.ofSeconds(30));

        assertEquals(1, report.published());
        assertEquals(1, report.processed());
        assertEquals(token, published.get().leaseToken());
        assertEquals(token, delivery.ackToken.get());
    }

    @Test
    void publisherFailureIsRescheduledWithExponentialBackoff() {
        FakeDelivery delivery = new FakeDelivery(message(UUID.randomUUID(), 3));
        QueueEventPublisher publisher = event -> { throw new IllegalStateException("broker unavailable"); };
        QueueOutboxDispatchService service = new QueueOutboxDispatchService(delivery, publisher);

        var report = service.dispatchOnce(TENANT, "worker-a", 10, Duration.ofSeconds(30),
                8, Duration.ofSeconds(2), Duration.ofSeconds(20));

        assertEquals(1, report.rescheduled());
        assertEquals(Duration.ofSeconds(8), delivery.retryDelay.get());
        assertEquals("IllegalStateException: broker unavailable", delivery.error.get());
    }

    @Test
    void publisherFailureAtAttemptLimitDeadLetters() {
        FakeDelivery delivery = new FakeDelivery(message(UUID.randomUUID(), 4));
        QueueOutboxDispatchService service = new QueueOutboxDispatchService(delivery,
                event -> { throw new IllegalArgumentException("invalid event"); });

        var report = service.dispatchOnce(TENANT, "worker-a", 10, Duration.ofSeconds(30),
                4, Duration.ofSeconds(1), Duration.ofSeconds(10));

        assertEquals(1, report.deadLettered());
    }

    @Test
    void invalidRetryBoundsAreRejectedBeforeClaiming() {
        FakeDelivery delivery = new FakeDelivery(message(UUID.randomUUID(), 1));
        QueueOutboxDispatchService service = new QueueOutboxDispatchService(delivery, event -> {});

        assertThrows(IllegalArgumentException.class, () -> service.dispatchOnce(TENANT, "worker-a", 10,
                Duration.ofSeconds(30), 4, Duration.ofSeconds(10), Duration.ofSeconds(2)));
        assertEquals(0, delivery.claims);
    }

    private static QueueOutboxMessage message(UUID token, int attempt) {
        return new QueueOutboxMessage(UUID.randomUUID(), TENANT, "QueueEntryJoined", 1,
                "QUEUE_ENTRY", "entry-1", "corr-1", Instant.now(), "{}", attempt,
                "worker-a", token, Instant.now().plusSeconds(30));
    }

    private static final class FakeDelivery implements QueueOutboxDeliveryPort {
        private final QueueOutboxMessage event;
        private final AtomicReference<UUID> ackToken = new AtomicReference<>();
        private final AtomicReference<Duration> retryDelay = new AtomicReference<>();
        private final AtomicReference<String> error = new AtomicReference<>();
        private int claims;

        private FakeDelivery(QueueOutboxMessage event) { this.event = event; }

        @Override
        public List<QueueOutboxMessage> claim(UUID tenantId, String workerId, int limit, Duration leaseDuration) {
            claims++;
            return List.of(event);
        }

        @Override
        public boolean acknowledge(UUID tenantId, UUID eventId, UUID leaseToken) {
            ackToken.set(leaseToken);
            return true;
        }

        @Override
        public RetryOutcome retry(UUID tenantId, UUID eventId, UUID leaseToken, Duration delay,
                                  int maxAttempts, String failure) {
            retryDelay.set(delay);
            error.set(failure);
            return event.attemptCount() >= maxAttempts ? RetryOutcome.DEAD_LETTERED : RetryOutcome.RESCHEDULED;
        }
    }
}
