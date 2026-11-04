package tech.kayys.numou.queuemanagement.application;

import tech.kayys.numou.queuemanagement.spi.QueueEventPublisher;
import tech.kayys.numou.queuemanagement.spi.QueueOutboxDeliveryPort;
import tech.kayys.numou.queuemanagement.spi.QueueOutboxMessage;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** Coordinates bounded outbox batches without holding a database transaction during transport I/O. */
public class QueueOutboxDispatchService {
    private final QueueOutboxDeliveryPort delivery;
    private final QueueEventPublisher publisher;

    public QueueOutboxDispatchService(QueueOutboxDeliveryPort delivery, QueueEventPublisher publisher) {
        this.delivery = Objects.requireNonNull(delivery, "delivery must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    public DispatchReport dispatchOnce(UUID tenantId, String workerId, int batchSize,
                                        Duration leaseDuration, int maxAttempts,
                                        Duration initialRetryDelay, Duration maximumRetryDelay) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(initialRetryDelay, "initialRetryDelay");
        Objects.requireNonNull(maximumRetryDelay, "maximumRetryDelay");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        if (initialRetryDelay.isNegative() || maximumRetryDelay.isNegative()
                || maximumRetryDelay.compareTo(initialRetryDelay) < 0) {
            throw new IllegalArgumentException("Retry delay bounds are invalid");
        }

        int published = 0;
        int rescheduled = 0;
        int deadLettered = 0;
        int leaseLost = 0;
        for (QueueOutboxMessage event : delivery.claim(tenantId, workerId, batchSize, leaseDuration)) {
            try {
                publisher.publish(event);
                if (delivery.acknowledge(tenantId, event.eventId(), event.leaseToken())) published++;
                else leaseLost++;
            } catch (Exception failure) {
                Duration delay = retryDelay(initialRetryDelay, maximumRetryDelay, event.attemptCount());
                QueueOutboxDeliveryPort.RetryOutcome outcome = delivery.retry(
                        tenantId, event.eventId(), event.leaseToken(), delay, maxAttempts,
                        safeMessage(failure));
                switch (outcome) {
                    case RESCHEDULED -> rescheduled++;
                    case DEAD_LETTERED -> deadLettered++;
                    case LEASE_LOST -> leaseLost++;
                }
            }
        }
        return new DispatchReport(published, rescheduled, deadLettered, leaseLost);
    }

    private static Duration retryDelay(Duration initial, Duration maximum, int attempt) {
        long cap = maximum.toSeconds();
        long seconds = initial.toSeconds();
        if (seconds == 0 || cap == 0) return Duration.ZERO;
        for (int i = 1; i < attempt && seconds < cap; i++) {
            seconds = seconds > cap / 2 ? cap : Math.min(cap, seconds * 2);
        }
        return Duration.ofSeconds(Math.min(seconds, cap));
    }

    private static String safeMessage(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return failure.getClass().getSimpleName();
        return failure.getClass().getSimpleName() + ": " + message;
    }

    public record DispatchReport(int published, int rescheduled, int deadLettered, int leaseLost) {
        public int processed() { return published + rescheduled + deadLettered + leaseLost; }
    }
}
