package tech.kayys.syirkah.reliability.application.outbox;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.event.spi.port.EventPublisherPort;
import tech.kayys.syirkah.reliability.failure.FailureClassification;
import tech.kayys.syirkah.reliability.outbox.OutboxEntry;
import tech.kayys.syirkah.reliability.retry.RetryPolicy;
import tech.kayys.syirkah.reliability.spi.port.OutboxRelayPort;
import tech.kayys.syirkah.reliability.spi.port.OutboxStorePort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Drains the transactional outbox to the event transport (at-least-once).
 *
 * <p>Delivery guarantees and their limits, stated plainly:
 * <ul>
 *   <li>An entry is marked published only after the transport acknowledged it. A crash
 *       between ack and mark re-delivers it: consumers must deduplicate by event id
 *       (see the inbox).</li>
 *   <li>A failed entry is rescheduled with exponential backoff + jitter. While it waits,
 *       later entries may overtake it, so ordering is <em>not</em> guaranteed across
 *       retries; consumers must tolerate that (aggregate versions).</li>
 *   <li>PERMANENT failures (e.g. an unserialisable payload) skip the retry ladder and are
 *       dead-lettered immediately so one poison entry cannot burn retries forever.</li>
 *   <li>Entries beyond {@link OutboxEntry#MAX_ATTEMPTS} stay in the table, are excluded
 *       from {@code nextDue}, and are expected to raise an alert (dead-letter count).</li>
 * </ul>
 */
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
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        this.batchSize = batchSize;
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.classifier = Objects.requireNonNull(classifier, "classifier cannot be null");
    }

    public static Function<Throwable, FailureClassification> defaultClassifier() {
        return t -> t instanceof IllegalArgumentException || t instanceof ClassCastException
                ? FailureClassification.PERMANENT
                : FailureClassification.RETRYABLE;
    }

    @Override
    public Uni<Void> relay(OutboxEntry entry) {
        return publisher.publish(entry.envelope())
                .flatMap(ignored -> store.update(entry.markPublished()));
    }

    @Override
    public Uni<Integer> runOnce() {
        return store.nextDue(batchSize).flatMap(due -> relayAll(due, 0, 0));
    }

    /** Sequential on purpose: preserves order inside a batch and bounds load on the transport. */
    private Uni<Integer> relayAll(List<OutboxEntry> due, int index, int relayed) {
        if (index >= due.size()) {
            return Uni.createFrom().item(relayed);
        }
        final OutboxEntry entry = due.get(index);
        return relay(entry)
                .map(ok -> 1)
                .onFailure().recoverWithUni(failure -> recordFailure(entry, failure).replaceWith(0))
                .flatMap(delta -> relayAll(due, index + 1, relayed + delta));
    }

    private Uni<Void> recordFailure(OutboxEntry entry, Throwable failure) {
        final var classification = classifier.apply(failure);
        final Instant now = clock.get();
        final OutboxEntry next;
        if (!retryPolicy.allows(classification)) {
            next = new OutboxEntry(entry.entryId(), entry.envelope(), entry.createdAt(), now,
                    OutboxEntry.MAX_ATTEMPTS, false); // dead-letter now
        } else {
            final var delay = retryPolicy.delayFor(entry.attempts() + 1, random.getAsDouble());
            next = entry.recordFailure(now.plus(delay));
        }
        return store.update(next);
    }
}
