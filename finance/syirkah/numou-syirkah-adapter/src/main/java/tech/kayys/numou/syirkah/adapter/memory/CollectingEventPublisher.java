package tech.kayys.numou.numouadapter.memory;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.numouapplication.port.EventPublisherPort;

import java.util.ArrayList;
import java.util.List;

/** Collects published events (stand-in for the transactional outbox). */
public class CollectingEventPublisher implements EventPublisherPort {

    private final List<DomainEvent> events = new ArrayList<>();

    @Override
    public synchronized void publish(TenantId tenant, List<DomainEvent> batch) {
        events.addAll(batch);
    }

    public synchronized List<DomainEvent> events() {
        return List.copyOf(events);
    }
}
