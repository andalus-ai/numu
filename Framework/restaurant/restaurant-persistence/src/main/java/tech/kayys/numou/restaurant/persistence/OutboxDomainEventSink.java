package tech.kayys.numou.restaurant.persistence;

import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.mutiny.Uni;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.reliability.outbox.OutboxEntry;
import tech.kayys.numou.reliability.spi.port.OutboxStorePort;
import tech.kayys.numou.restaurant.kernel.spi.DomainEventSink;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Production {@link DomainEventSink}: appends events to the transactional outbox. Called inside the unit of work, so
 * the append commits (or rolls back) together with the aggregate change - no event without its state, no state
 * without its event.
 *
 * <p>The envelope carries tenant, actor and correlation id so consumers and traces can attribute every event.
 * {@code eventId}/{@code occurredAt}/{@code eventType} live in the envelope and are not repeated in {@code data}.
 */
public final class OutboxDomainEventSink implements DomainEventSink {

    private final OutboxStorePort outbox;
    private final ObjectMapper mapper;
    private final Supplier<String> actor;
    private final Supplier<String> correlationId;

    public OutboxDomainEventSink(OutboxStorePort outbox, ObjectMapper mapper, Supplier<String> actor, Supplier<String> correlationId) {
        this.outbox = Objects.requireNonNull(outbox, "outbox cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.actor = Objects.requireNonNull(actor, "actor cannot be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId cannot be null");
    }

    @Override
    public Uni<Void> publish(UUID tenantId, List<DomainEvent> events) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (events.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        final var entries = events.stream().map(e -> toEntry(tenantId, e)).toList();
        return outbox.append(entries);
    }

    private OutboxEntry toEntry(UUID tenantId, DomainEvent e) {
        final ObjectNode data = mapper.valueToTree(e);
        data.remove("eventId");
        data.remove("occurredAt");
        final var payload = new JsonDomainEvent(e.eventId(), e.occurredAt().toString(), e.eventType(), data);
        final var meta = EventMetadata.of(tenantId, null, actor.get(), CorrelationId.of(correlationId.get()));
        // keep the domain event's own identity so redelivery/dedupe use the id the aggregate generated
        final var withId = new EventMetadata(tech.kayys.numou.event.domain.identifier.EventId.of(e.eventId()), meta.correlationId(),
                meta.causationId(), meta.tenantId(), meta.participantId(), meta.actorId(), e.occurredAt().toString(), meta.traceId());
        return OutboxEntry.pending(new EventEnvelope<>(withId, payload.eventVersion(), payload));
    }
}
