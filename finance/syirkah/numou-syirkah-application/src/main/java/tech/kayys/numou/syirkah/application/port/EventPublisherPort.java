package tech.kayys.numou.numouapplication.port;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;

/**
 * Publishes domain events; implementations should write to the transactional
 * outbox.
 */
public interface EventPublisherPort {
    void publish(TenantId tenant, List<DomainEvent> events);
}
