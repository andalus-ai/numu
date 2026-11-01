package tech.kayys.numou.reliability.application.inbox;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.reliability.spi.port.InboxStorePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Deduplicates inbound events and serializes concurrent delivery with a durable lease.
 * Delivery remains at-least-once: handlers must be idempotent or commit their business
 * effects and inbox completion in the same transaction.
 */
public final class InboxProcessor {
    private static final Duration DEFAULT_CLAIM_LEASE = Duration.ofMinutes(5);
    private final InboxStorePort inbox;
    private final Duration claimLease;

    public InboxProcessor(InboxStorePort inbox) {
        this(inbox, DEFAULT_CLAIM_LEASE);
    }

    public InboxProcessor(InboxStorePort inbox, Duration claimLease) {
        this.inbox = Objects.requireNonNull(inbox, "inbox cannot be null");
        this.claimLease = Objects.requireNonNull(claimLease, "claimLease cannot be null");
        if (claimLease.isZero() || claimLease.isNegative()) {
            throw new IllegalArgumentException("claimLease must be positive");
        }
    }

    /**
     * @return true when this invocation acquired the claim and handled the event;
     * false when already handled or another consumer owns a live claim.
     */
    public Uni<Boolean> process(String eventId, String eventType, Supplier<Uni<Void>> handler) {
        Objects.requireNonNull(handler, "handler cannot be null");
        return inbox.recordArrival(eventId, eventType).flatMap(arrival -> {
            if (arrival.entry().alreadyProcessed()) return Uni.createFrom().item(false);
            final UUID token = UUID.randomUUID();
            final Instant now = Instant.now();
            return inbox.claim(eventId, token, now, now.plus(claimLease)).flatMap(acquired -> {
                if (!acquired) return Uni.createFrom().item(false);
                // Only handler failure releases the claim. If completion persistence
                // fails after a successful side effect, retain it until lease expiry.
                return Uni.createFrom().<Void>deferred(handler::get)
                        .onFailure().recoverWithUni(failure ->
                                inbox.releaseClaim(eventId, token)
                                        .onFailure().recoverWithItem((Void) null)
                                        .flatMap(ignored -> Uni.<Void>createFrom().failure(failure)))
                        .flatMap(ignored -> inbox.markHandled(eventId, token))
                        .replaceWith(true);
            });
        });
    }
}
