package tech.kayys.syirkah.construction.application.outbox;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.spi.outbox.OutboxDeliveryPort;
import tech.kayys.syirkah.construction.spi.outbox.OutboxRelayStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates at-least-once outbox delivery without holding a database
 * transaction open while the broker call runs. Store implementations must
 * claim rows atomically and validate lease tokens on every state change.
 *
 * <p>Scheduling, metrics, and lifecycle management intentionally belong to the
 * adapter/runtime module because the source dump does not establish a single
 * scheduler convention for construction.</p>
 */
public final class OutboxRelayService {
    private final OutboxRelayStore store;
    private final OutboxDeliveryPort delivery;
    private final OutboxRetryPolicy retryPolicy;
    private final Clock clock;
    private final Duration leaseDuration;
    private final int batchSize;
    private final String workerId;

    public OutboxRelayService(
            OutboxRelayStore store,
            OutboxDeliveryPort delivery,
            OutboxRetryPolicy retryPolicy,
            Clock clock,
            Duration leaseDuration,
            int batchSize,
            String workerId) {
        this.store = Objects.requireNonNull(store, "store");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be >= 1");
        if (workerId == null || workerId.isBlank()) throw new IllegalArgumentException("workerId is required");
        this.batchSize = batchSize;
        this.workerId = workerId;
    }

    /** Claims one batch, then delivers outside the claim transaction. */
    public Uni<Integer> runOnce() {
        Instant claimedAt = clock.instant();
        return store.claimBatch(workerId, claimedAt, claimedAt.plus(leaseDuration), batchSize)
                .onItem().transformToUni(events -> processBatch(events));
    }

    private Uni<Integer> processBatch(List<OutboxRelayStore.ClaimedOutboxEvent> events) {
        if (events == null || events.isEmpty()) return Uni.createFrom().item(0);
        List<Uni<Void>> work = events.stream().map(this::deliverOne).toList();
        return Uni.combine().all().unis(work).discardItems().replaceWith(events.size());
    }

    private Uni<Void> deliverOne(OutboxRelayStore.ClaimedOutboxEvent event) {
        return delivery.deliver(event)
                .onItem().transformToUni(ignored ->
                        store.markPublished(event.eventId(), event.leaseToken(), clock.instant()).replaceWithVoid())
                .onFailure().recoverWithUni(failure -> recordFailure(event, failure));
    }

    private Uni<Void> recordFailure(OutboxRelayStore.ClaimedOutboxEvent event, Throwable failure) {
        int attempts = event.attempts();
        boolean terminal = retryPolicy.isTerminal(attempts);
        Instant failedAt = clock.instant();
        Instant retryAt = terminal ? failedAt : retryPolicy.nextAttemptAt(failedAt, attempts);
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        if (message.length() > 2000) message = message.substring(0, 2000);
        return store.markFailed(event.eventId(), event.leaseToken(), retryAt, message, terminal)
                .replaceWithVoid();
    }
}
