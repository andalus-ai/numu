package tech.kayys.payment.adapter.postgres;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Processes a bounded payment-outbox batch. Scheduling belongs to the host
 * runtime; this method can be called by a scheduler, worker, or operations job.
 */
@ApplicationScoped
public class PaymentOutboxRelay {
    static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);

    private final PaymentOutboxRelayStore store;
    private final PaymentOutboxDeliveryPort delivery;
    private final Clock clock;

    @Inject
    public PaymentOutboxRelay(PaymentOutboxRelayStore store, PaymentOutboxDeliveryPort delivery) {
        this(store, delivery, Clock.systemUTC());
    }

    PaymentOutboxRelay(PaymentOutboxRelayStore store, PaymentOutboxDeliveryPort delivery, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Claims and processes one batch; returns the number of claimed events. */
    public int runOnce(String workerId, int batchSize) {
        Instant now = clock.instant();
        List<PaymentOutboxRelayStore.ClaimedEvent> events =
                store.claimBatch(workerId, now, now.plus(LEASE_DURATION), batchSize);
        for (PaymentOutboxRelayStore.ClaimedEvent event : events) {
            deliverOne(event);
        }
        return events.size();
    }

    private void deliverOne(PaymentOutboxRelayStore.ClaimedEvent event) {
        try {
            delivery.deliver(event);
            // A false result means this worker lost its lease. It must not retry
            // or overwrite the newer worker's state.
            store.markPublished(event.eventId(), event.leaseToken(), clock.instant());
        } catch (Exception failure) {
            Instant failedAt = clock.instant();
            boolean terminal = event.attempts() >= event.maxAttempts();
            Instant retryAt = terminal ? failedAt : failedAt.plus(retryDelay(event.attempts()));
            String message = failure.getMessage();
            if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
            if (message.length() > 2000) message = message.substring(0, 2000);
            store.markFailed(event.eventId(), event.leaseToken(), retryAt, message, terminal);
        }
    }

    private static Duration retryDelay(int attempts) {
        long seconds = Math.min(MAX_RETRY_DELAY.toSeconds(), 1L << Math.min(attempts, 12));
        return Duration.ofSeconds(seconds);
    }
}
