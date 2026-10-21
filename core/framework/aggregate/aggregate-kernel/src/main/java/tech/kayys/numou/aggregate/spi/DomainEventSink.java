package tech.kayys.numou.aggregate.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.List;
import java.util.UUID;

/**
 * Where domain events go after an aggregate is saved. In production this appends to the transactional outbox
 * <em>inside the same database transaction</em> as the aggregate write; in tests it just records.
 */
public interface DomainEventSink {

    Uni<Void> publish(UUID tenantId, List<DomainEvent> events);
}
