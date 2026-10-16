package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.application.port.IdentityOutboxPort;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PostgresIdentityOutbox implements IdentityOutboxPort {
    private final ObjectMapper objectMapper;

    @Inject
    public PostgresIdentityOutbox(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Uni<Void> append(TenantId tenantId, List<DomainEvent> events) {
        var tenantUuid = tenantId.value();
        Uni<Void> writes = Uni.createFrom().voidItem();
        for (var event : List.copyOf(events)) {
            writes = writes.chain(() -> appendOne(tenantUuid, event));
        }
        return writes;
    }

    private Uni<Void> appendOne(UUID tenantId, DomainEvent event) {
        return IdentityOutboxEntity.<IdentityOutboxEntity>find("eventId", event.eventId())
                .firstResult()
                .chain(existing -> {
                    if (existing != null) {
                        if (existing.tenantId != null && !existing.tenantId.equals(tenantId)
                                || !existing.eventType.equals(event.eventType())) {
                            return Uni.createFrom().failure(new IllegalStateException("Outbox event ID collision"));
                        }
                        return Uni.createFrom().voidItem();
                    }
                    var row = new IdentityOutboxEntity();
                    row.id = UUID.randomUUID();
                    row.eventId = event.eventId();
                    row.tenantId = tenantId;
                    row.aggregateType = event.getClass().getSimpleName();
                    row.aggregateId = event.eventId().toString();
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
            throw new IllegalStateException("Could not serialize identity domain event", exception);
        }
    }
}
