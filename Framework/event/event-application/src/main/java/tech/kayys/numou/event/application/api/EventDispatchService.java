package tech.kayys.numou.event.application.api;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.event.application.mapper.DomainEventTranslatorRegistry;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Turns a list of domain events into published business events
 * (base01.md §P1-13).
 *
 * <p>Domain events that no translator claims are silently skipped: they
 * were never public contracts. The rest are wrapped in an envelope carrying
 * tenant, correlation and causation context, then handed to the publisher
 * port - so the same service works whether the transport is Kafka, a
 * webhook or a transactional outbox.
 */
public final class EventDispatchService {

    private final DomainEventTranslatorRegistry translators;
    private final EventPublisherPort publisher;

    public EventDispatchService(
            DomainEventTranslatorRegistry translators,
            EventPublisherPort publisher) {
        this.translators = Objects.requireNonNull(translators, "translators cannot be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher cannot be null");
    }

    /**
     * Publishes the translatable subset of the given domain events.
     *
     * @param domainEvents   events pulled from an aggregate
     * @param tenantId       isolation boundary of the operation
     * @param participantId  ecosystem participant that acted
     * @param actorId        user or system principal
     * @param correlationId  whole-journey correlation
     */
    public Uni<Void> dispatch(
            List<DomainEvent> domainEvents,
            UUID tenantId,
            UUID participantId,
            String actorId,
            CorrelationId correlationId) {

        if (domainEvents == null || domainEvents.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        final var metadata = EventMetadata.of(tenantId, participantId, actorId, correlationId);

        final List<EventEnvelope<? extends BusinessEvent>> envelopes = new java.util.ArrayList<>();

        for (final var domainEvent : domainEvents) {
            final var translated = translators.translate(domainEvent);
            if (translated.isEmpty()) {
                // No translator claims it: this event stays private to its
                // bounded context and is never published.
                continue;
            }
            final BusinessEvent businessEvent = translated.get();
            envelopes.add(EventEnvelope.of(metadata, businessEvent));
        }

        if (envelopes.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        return publisher.publishAll(envelopes);
    }
}
