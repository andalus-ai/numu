package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;
import tech.kayys.numou.affiliate.spi.AffiliateEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Executes one bounded, tenant-scoped delivery pass.
 *
 * Delivery is at-least-once: a process can publish successfully and crash
 * before acknowledging the lease. Consumers must deduplicate by eventKey.
 * Scheduling and transport selection remain deployment concerns.
 */
public final class AffiliateOutboxDispatcher {
    private final AffiliateEventOutboxStore store;
    private final AffiliateEventPublisher publisher;
    private final Clock clock;
    private final Duration lease;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final int batchSize;
    private final int maxAttempts;

    public AffiliateOutboxDispatcher(AffiliateEventOutboxStore store, AffiliateEventPublisher publisher,
            Clock clock, Duration lease, Duration initialBackoff, Duration maxBackoff,
            int batchSize, int maxAttempts) {
        this.store = Objects.requireNonNull(store);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
        this.lease = positive(lease, "lease");
        this.initialBackoff = positive(initialBackoff, "initialBackoff");
        this.maxBackoff = positive(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0) throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        if (batchSize < 1 || batchSize > 500 || maxAttempts < 1) throw new IllegalArgumentException("invalid batchSize or maxAttempts");
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    public CompletionStage<Integer> dispatchOnce(String tenantId, String workerId) {
        if (tenantId == null || tenantId.isBlank() || workerId == null || workerId.isBlank())
            return CompletableFuture.failedFuture(new IllegalArgumentException("tenantId and workerId are required"));
        Instant now = clock.instant();
        return store.claimPending(tenantId, workerId, now, lease, batchSize)
                .thenCompose(events -> dispatch(events, 0, workerId, new DispatchProgress(0, null)))
                .thenCompose(progress -> progress.failure() == null
                        ? CompletableFuture.completedFuture(progress.delivered())
                        : CompletableFuture.failedFuture(
                                new AffiliateOutboxDispatchException(progress.delivered(), progress.failure())));
    }

    /**
     * Processes every row in the claimed batch even if a stale lease or a
     * persistence failure prevents one row from being rescheduled. The
     * remaining rows are independent claims and must not be stranded merely
     * because an earlier row could not be completed.
     */
    private CompletionStage<DispatchProgress> dispatch(
            List<AffiliateEventOutboxStore.ClaimedEvent> events,
            int index, String workerId, DispatchProgress progress) {
        if (index >= events.size()) return CompletableFuture.completedFuture(progress);

        AffiliateEventOutboxStore.ClaimedEvent event = events.get(index);
        CompletionStage<Void> publication;
        try {
            publication = Objects.requireNonNull(publisher.publish(event), "publisher returned null")
                    .thenCompose(ignored -> store.markDelivered(event.tenantId(), event.id(),
                            workerId, event.leaseVersion(), clock.instant()));
        } catch (RuntimeException failure) {
            publication = CompletableFuture.failedFuture(failure);
        }

        return publication.handle((ignored, failure) -> {
            if (failure == null) {
                return CompletableFuture.completedFuture(
                        new DispatchProgress(progress.delivered() + 1, progress.failure()));
            }

            Instant now = clock.instant();
            CompletionStage<Void> retry;
            try {
                retry = Objects.requireNonNull(store.reschedule(event.tenantId(), event.id(), workerId,
                        describe(failure), now.plus(backoff(event.attempts())), now,
                        maxAttempts, event.leaseVersion()), "outbox store returned null reschedule stage");
            } catch (RuntimeException rescheduleFailure) {
                return CompletableFuture.completedFuture(withFailure(progress, rescheduleFailure));
            }
            return retry.handle((rescheduled, rescheduleFailure) -> rescheduleFailure == null
                    ? progress
                    : withFailure(progress, rescheduleFailure));
        }).thenCompose(stage -> stage)
                .thenCompose(next -> dispatch(events, index + 1, workerId, next));
    }

    private static DispatchProgress withFailure(DispatchProgress progress, Throwable failure) {
        Throwable cause = unwrap(failure);
        if (progress.failure() == null) return new DispatchProgress(progress.delivered(), cause);
        if (progress.failure() != cause) progress.failure().addSuppressed(cause);
        return progress;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private record DispatchProgress(int delivered, Throwable failure) {}

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(30, Math.max(0, attempt - 1));
        try {
            Duration candidate = initialBackoff.multipliedBy(multiplier);
            return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
        } catch (ArithmeticException overflow) {
            return maxBackoff;
        }
    }

    private static String describe(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null)
            cause = cause.getCause();
        String message = cause.getMessage();
        String value = cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
