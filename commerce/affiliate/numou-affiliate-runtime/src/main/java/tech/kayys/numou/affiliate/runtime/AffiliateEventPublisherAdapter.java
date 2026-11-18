package tech.kayys.numou.affiliate.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.ClaimedEvent;
import tech.kayys.numou.affiliate.spi.AffiliateEventPublisher;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.spi.port.EventPublisherPort;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Bridges the Affiliate durable outbox to Numou's versioned business-event
 * platform. Tenant UUID resolution and authenticated actor/correlation
 * metadata remain host responsibilities because Affiliate tenant IDs are
 * opaque deployment identifiers.
 */
public final class AffiliateEventPublisherAdapter implements AffiliateEventPublisher {
    private final EventPublisherPort eventPublisher;
    private final AffiliateEventMetadataResolver metadataResolver;

    public AffiliateEventPublisherAdapter(EventPublisherPort eventPublisher,
            AffiliateEventMetadataResolver metadataResolver) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.metadataResolver = Objects.requireNonNull(metadataResolver, "metadataResolver");
    }

    @Override
    public CompletionStage<Void> publish(ClaimedEvent event) {
        Objects.requireNonNull(event, "event");
        EventMetadata metadata = Objects.requireNonNull(
                metadataResolver.resolve(event), "resolved event metadata");
        if (!event.id().equals(metadata.eventId().value())) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("resolved event metadata must preserve the outbox event ID"));
        }
        if (!event.occurredAt().toString().equals(metadata.occurredAt())) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("resolved event metadata must preserve outbox occurredAt"));
        }

        AffiliateOutboxBusinessEvent payload = new AffiliateOutboxBusinessEvent(
                event.id(), event.occurredAt(), event.eventType(), event.eventKey(),
                event.aggregateType(), event.aggregateId(), event.attempts(), event.payload());
        EventEnvelope<AffiliateOutboxBusinessEvent> envelope = EventEnvelope.of(metadata, payload);
        Uni<Void> publication = eventPublisher.publish(envelope);
        return publication.subscribeAsCompletionStage();
    }
}
