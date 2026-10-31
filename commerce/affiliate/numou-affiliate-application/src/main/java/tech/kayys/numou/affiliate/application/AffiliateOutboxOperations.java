package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Operator action for retrying a terminally failed integration event.
 * The host must construct the command context only after authenticating and
 * authorizing the operator for the supplied tenant.
 */
public final class AffiliateOutboxOperations {
    private final AffiliateEventOutboxStore store;
    private final Clock clock;

    public AffiliateOutboxOperations(AffiliateEventOutboxStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<Boolean> redriveDeadLetter(AffiliateCommandContext context,
            UUID eventId, String reason) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(eventId, "eventId");
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000) {
            throw new IllegalArgumentException("redrive reason must contain 1..1000 characters");
        }
        return store.redriveDeadLetter(context.tenantId(), eventId, context.actorId(),
                reason.trim(), context.idempotencyKey(), context.correlationId(), clock.instant());
    }
}
