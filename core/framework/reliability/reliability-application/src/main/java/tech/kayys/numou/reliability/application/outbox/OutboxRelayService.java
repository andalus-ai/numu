package tech.kayys.numou.reliability.application.outbox;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.reliability.failure.FailureClassification;
import tech.kayys.numou.reliability.outbox.OutboxClaim;
import tech.kayys.numou.reliability.outbox.OutboxEntry;
import tech.kayys.numou.reliability.retry.RetryPolicy;
import tech.kayys.numou.reliability.spi.port.OutboxRelayPort;
import tech.kayys.numou.reliability.spi.port.OutboxStorePort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/** Drains the transactional outbox with token-fenced lease ownership (at-least-once). */
public final class OutboxRelayService implements OutboxRelayPort {
    private final OutboxStorePort store;
    private final EventPublisherPort publisher;
    private final RetryPolicy retryPolicy;
    private final int batchSize;
    private final Supplier<Instant> clock;
    private final DoubleSupplier random;
    private final Function<Throwable, FailureClassification> classifier;

    public OutboxRelayService(OutboxStorePort store, EventPublisherPort publisher, RetryPolicy retryPolicy,
                              int batchSize, Supplier<Instant> clock, DoubleSupplier random,
                              Function<Throwable, FailureClassification> classifier) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher cannot be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy cannot be null");
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be >= 1");
        this.batchSize = batchSize;
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.classifier = Objects.requireNonNull(classifier, "classifier cannot be null");
    }

    public static Function<Throwable, FailureClassification> defaultClassifier() {
        return t -> t instanceof IllegalArgumentException || t instanceof ClassCastException
                ? FailureClassification.PERMANENT : FailureClassification.RETRYABLE;
    }

    @Override
    public Uni<Void> relay(OutboxClaim claim) {
        final OutboxEntry entry = claim.entry();
        return publisher.publish(entry.envelope())
                .flatMap(ignored -> store.update(claim, entry.markPublished()))
                .flatMap(updated -> updated
                        ? Uni.createFrom().voidItem()
                        : Uni.createFrom().failure(new IllegalStateException(
                                "Outbox lease expired or was superseded before publish acknowledgement was recorded")));
    }

    @Override
    public Uni<Integer> runOnce() {
        return relayNext(0, 0);
    }

    /**
     * Claim one row immediately before publishing it. Claiming an entire sequential
     * batch up front lets later rows' leases expire while earlier rows are in flight.
     */
    private Uni<Integer> relayNext(int processed, int relayed) {
        if (processed >= batchSize) return Uni.createFrom().item(relayed);
        return store.nextDue(1).flatMap(due -> {
            if (due.isEmpty()) return Uni.createFrom().item(relayed);
            final OutboxClaim claim = due.get(0);
            return relay(claim).map(ok -> 1)
                    .onFailure().recoverWithUni(failure -> recordFailure(claim, failure).replaceWith(0))
                    .flatMap(delta -> relayNext(processed + 1, relayed + delta));
        });
    }

    private Uni<Void> recordFailure(OutboxClaim claim, Throwable failure) {
        final var classification = classifier.apply(failure);
        final Instant now = clock.get();
        final OutboxEntry entry = claim.entry();
        final OutboxEntry next;
        if (!retryPolicy.allows(classification)) {
            next = new OutboxEntry(entry.entryId(), entry.envelope(), entry.createdAt(), now,
                    OutboxEntry.MAX_ATTEMPTS, false);
        } else {
            final var delay = retryPolicy.delayFor(entry.attempts() + 1, random.getAsDouble());
            next = entry.recordFailure(now.plus(delay));
        }
        // A stale claim cannot overwrite the current owner's state.
        return store.update(claim, next).replaceWithVoid();
    }
}
