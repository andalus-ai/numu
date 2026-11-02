package tech.kayys.numou.security.vault.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.security.vault.spi.VaultAuditOutboxEvent;
import tech.kayys.numou.security.vault.spi.VaultAuditOutboxPort;
import tech.kayys.numou.security.vault.spi.VaultAuditPublisherPort;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Bounded at-least-once relay. Consumers must deduplicate by eventId because a
 * publish can succeed while the acknowledgement is lost.
 */
public final class VaultAuditOutboxRelay {
    private static final int MAX_ATTEMPTS = 12;
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final VaultAuditOutboxPort outbox;
    private final VaultAuditPublisherPort publisher;

    public VaultAuditOutboxRelay(VaultAuditOutboxPort outbox, VaultAuditPublisherPort publisher) {
        this.outbox = Objects.requireNonNull(outbox, "outbox is required");
        this.publisher = Objects.requireNonNull(publisher, "publisher is required");
    }

    /** Claims no more than limit records and processes each independently. */
    public Uni<Integer> dispatch(String workerId, Instant now, Duration leaseDuration, int limit) {
        if (workerId == null || workerId.isBlank() || workerId.length() > 160)
            return Uni.createFrom().failure(new IllegalArgumentException("workerId must contain 1..160 characters"));
        Objects.requireNonNull(now, "now is required");
        Objects.requireNonNull(leaseDuration, "leaseDuration is required");
        if (leaseDuration.isNegative() || leaseDuration.isZero())
            return Uni.createFrom().failure(new IllegalArgumentException("leaseDuration must be positive"));
        if (limit < 1 || limit > 500)
            return Uni.createFrom().failure(new IllegalArgumentException("limit must be between 1 and 500"));

        return outbox.claimDue(workerId, now, now.plus(leaseDuration), limit)
                .flatMap(events -> {
                    if (events.isEmpty()) return Uni.createFrom().item(0);
                    List<Uni<Void>> work = events.stream()
                            .map(event -> deliver(event, workerId))
                            .toList();
                    return Uni.join().all(work).andCollectFailures().replaceWith(events.size());
                });
    }

    private Uni<Void> deliver(VaultAuditOutboxEvent event, String workerId) {
        return publisher.publish(event)
                .flatMap(ignored -> outbox.markPublished(event.eventId(), workerId, Instant.now()))
                .onFailure().recoverWithUni(failure -> {
                    boolean deadLetter = event.attempts() >= MAX_ATTEMPTS;
                    Instant retryAt = Instant.now().plus(backoff(event.attempts()));
                    // Do not persist exception messages: providers can accidentally include
                    // credentials or payload fragments in them.
                    String safeError = failure.getClass().getSimpleName();
                    if (safeError.length() > 200) safeError = "PublishFailure";
                    return outbox.reschedule(event.eventId(), workerId, retryAt, safeError, deadLetter);
                });
    }

    private static Duration backoff(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 10);
        long seconds = Math.min(1L << exponent, MAX_BACKOFF.toSeconds());
        return Duration.ofSeconds(seconds);
    }
}
