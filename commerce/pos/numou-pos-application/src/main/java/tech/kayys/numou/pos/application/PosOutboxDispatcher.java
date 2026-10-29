package tech.kayys.numou.pos.application;

import tech.kayys.numou.pos.application.port.out.PosEventPublisher;
import tech.kayys.numou.pos.application.port.out.PosOutboxDelivery;
import tech.kayys.numou.pos.application.port.out.PosOutboxDeliveryStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * One bounded dispatch pass. Scheduling is deliberately external so deployments
 * can choose a managed scheduler, worker, or dedicated process.
 */
public final class PosOutboxDispatcher {
    private final PosOutboxDeliveryStore store;
    private final PosEventPublisher publisher;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final int batchSize;
    private final int maxAttempts;

    public PosOutboxDispatcher(PosOutboxDeliveryStore store, PosEventPublisher publisher,
            Clock clock, Duration leaseDuration, Duration initialBackoff, Duration maxBackoff,
            int batchSize, int maxAttempts) {
        this.store = Objects.requireNonNull(store);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.initialBackoff = positive(initialBackoff, "initialBackoff");
        this.maxBackoff = positive(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0) throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        if (batchSize < 1 || maxAttempts < 1) throw new IllegalArgumentException("batchSize and maxAttempts must be positive");
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /** Returns the number of events successfully published in this pass. */
    public int dispatchOnce() {
        Instant now = clock.instant();
        List<PosOutboxDelivery> deliveries = store.claimDue(now, now.plus(leaseDuration), batchSize);
        int published = 0;
        for (PosOutboxDelivery delivery : deliveries) {
            try {
                publisher.publish(delivery.event());
                if (store.markPublished(delivery.event().eventId(), delivery.leaseToken(), clock.instant())) published++;
            } catch (Exception failure) {
                boolean dead = delivery.attemptCount() >= maxAttempts;
                Instant retryAt = clock.instant().plus(backoff(delivery.attemptCount()));
                store.reschedule(delivery.event().eventId(), delivery.leaseToken(), retryAt,
                        describe(failure), dead);
            }
        }
        return published;
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(30, Math.max(0, attempt - 1));
        try {
            Duration candidate = initialBackoff.multipliedBy(multiplier);
            return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
        } catch (ArithmeticException overflow) {
            return maxBackoff;
        }
    }

    private static String describe(Exception failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return value.length() > 4000 ? value.substring(0, 4000) : value;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
