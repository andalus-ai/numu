package tech.kayys.numou.affiliate.application;

import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.DeadLetterCursor;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.DeadLetterPage;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.OutboxHealthSnapshot;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Tenant-scoped inspection and operator actions for the Affiliate outbox.
 * The host must construct command context only after authenticating and
 * authorizing the operator for the supplied tenant.
 */
public final class AffiliateOutboxOperations {
    private static final int MAX_PAGE_SIZE = 100;

    private final AffiliateEventOutboxStore store;
    private final Clock clock;

    public AffiliateOutboxOperations(AffiliateEventOutboxStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CompletionStage<DeadLetterPage> listDeadLetters(AffiliateCommandContext context,
            DeadLetterCursor before, int limit) {
        Objects.requireNonNull(context, "context");
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("dead-letter page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return store.findDeadLetters(context.tenantId(), before, limit);
    }

    /**
     * Returns global aggregate health for an authenticated internal operations surface.
     * Hosts must not expose this method through tenant-facing APIs.
     */
    public CompletionStage<OutboxHealthSnapshot> healthSnapshot() {
        return store.healthSnapshot();
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
