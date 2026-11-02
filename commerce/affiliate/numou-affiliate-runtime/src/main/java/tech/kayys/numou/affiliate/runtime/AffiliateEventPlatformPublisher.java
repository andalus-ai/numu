package tech.kayys.numou.affiliate.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.affiliate.spi.AffiliateEventPublisher;
import tech.kayys.numou.affiliate.spi.AffiliateEventOutboxStore.ClaimedEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.spi.port.EventPublisherPort;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Bridges Affiliate's durable outbox port to Numou's versioned event platform. */
public final class AffiliateEventPlatformPublisher implements AffiliateEventPublisher {
    private final EventPublisherPort publisher;
    private final AffiliateEventMetadataResolver metadataResolver;

    public AffiliateEventPlatformPublisher(
            EventPublisherPort publisher, AffiliateEventMetadataResolver metadataResolver) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.metadataResolver = Objects.requireNonNull(metadataResolver, "metadataResolver");
    }

    @Override
    public CompletionStage<Void> publish(ClaimedEvent event) {
        Objects.requireNonNull(event, "event");
        var payload = new AffiliateOutboxBusinessEvent(
                event.id(), event.occurredAt(), event.eventType(), event.eventKey(),
                event.aggregateType(), event.aggregateId(), event.attempts(), event.payload());
        EventMetadata metadata = Objects.requireNonNull(
                metadataResolver.resolve(event), "metadata resolver returned null");
        if (!event.id().equals(metadata.eventId().value())) {
            throw new IllegalArgumentException("resolved metadata event id must preserve the outbox event id");
        }
        if (metadata.tenantId() == null) {
            throw new IllegalArgumentException("Affiliate integration events require a resolved tenant UUID");
        }
        Uni<Void> result = Objects.requireNonNull(
                publisher.publish(EventEnvelope.of(metadata, payload)), "event publisher returned null");
        return result.subscribeAsCompletionStage();
    }
}