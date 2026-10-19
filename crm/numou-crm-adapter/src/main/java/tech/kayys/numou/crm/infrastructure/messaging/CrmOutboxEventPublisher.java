package tech.kayys.numou.crm.infrastructure.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.crm.infrastructure.persistence.entity.CrmOutboxEntity;
import tech.kayys.numou.foundation.adapter.context.TenantContext;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link EventPublisher} that writes CRM domain events to the transactional
 * outbox instead of talking to a broker directly.
 *
 * <p>Because it runs inside the handler's {@code UnitOfWork}, the aggregate
 * write and the outbox insert commit atomically; a separate relay delivers
 * rows to the broker afterwards (at-least-once).</p>
 */
@ApplicationScoped
public class CrmOutboxEventPublisher implements EventPublisher {

    @Override
    public Uni<Void> publish(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        String tenantId = TenantContext.getTenantId();
        List<CrmOutboxEntity> rows = new ArrayList<>(events.size());
        for (DomainEvent event : events) {
            CrmOutboxEntity row = new CrmOutboxEntity();
            row.id = UUID.randomUUID();
            row.tenantId = tenantId;
            row.aggregateType = "Crm";
            row.eventType = event.eventType();
            row.payload = event.toString();
            row.occurredAt = event.occurredAt();
            row.processed = false;
            rows.add(row);
        }
        return CrmOutboxEntity.persist(rows);
    }
}
