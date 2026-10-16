package tech.kayys.syirkah.document.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.accounting.domain.document.DocumentArchived;
import tech.kayys.syirkah.accounting.domain.document.DocumentCreated;
import tech.kayys.syirkah.accounting.domain.document.DocumentDeleted;
import tech.kayys.syirkah.accounting.domain.document.DocumentPublished;
import tech.kayys.syirkah.accounting.domain.document.DocumentVersionCreated;
import tech.kayys.syirkah.document.application.port.DocumentOutboxPort;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class PostgresDocumentOutbox implements DocumentOutboxPort {
    private final ObjectMapper objectMapper;

    @Inject
    public PostgresDocumentOutbox(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Uni<Void> append(String tenantId, List<DomainEvent> events) {
        var tenantUuid = PostgresDocumentRepository.parseUuid(tenantId, "tenantId");
        Uni<Void> writes = Uni.createFrom().voidItem();
        for (var event : List.copyOf(events)) {
            var envelope = envelope(event);
            writes = writes.chain(() -> appendOne(tenantUuid, event, envelope));
        }
        return writes;
    }

    private Uni<Void> appendOne(UUID tenantId, DomainEvent event, EventEnvelope envelope) {
        return DocumentOutboxEntity.<DocumentOutboxEntity>find("eventId", event.eventId())
                .firstResult()
                .chain(existing -> {
                    if (existing != null) {
                        if (!existing.tenantId.equals(tenantId)
                                || !existing.aggregateId.equals(envelope.aggregateId())
                                || !existing.eventType.equals(event.eventType())) {
                            return Uni.createFrom().failure(
                                    new IllegalStateException("Outbox event ID collision")
                            );
                        }
                        return Uni.createFrom().voidItem();
                    }
                    var row = new DocumentOutboxEntity();
                    row.id = UUID.randomUUID();
                    row.tenantId = tenantId;
                    row.eventId = event.eventId();
                    row.aggregateType = "Document";
                    row.aggregateId = envelope.aggregateId();
                    row.eventType = event.eventType();
                    row.payload = serialize(event);
                    row.createdAt = event.occurredAt();
                    row.nextAttemptAt = event.occurredAt();
                    row.attemptCount = 0;
                    row.status = "PENDING";
                    return Panache.getSession().chain(session -> session.persist(row));
                });
    }

    private String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize document domain event", exception);
        }
    }

    private static EventEnvelope envelope(DomainEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        var documentId = switch (event) {
            case DocumentCreated created -> created.documentId();
            case DocumentVersionCreated created -> created.documentId();
            case DocumentPublished published -> published.documentId();
            case DocumentArchived archived -> archived.documentId();
            case DocumentDeleted deleted -> deleted.documentId();
            default -> throw new IllegalArgumentException("Unsupported document outbox event: " + event.eventType());
        };
        return new EventEnvelope(documentId.value());
    }

    private record EventEnvelope(String aggregateId) {}
}
