package tech.kayys.numou.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.List;
import java.util.UUID;

public interface DomainEventSink {
    Uni<Void> publish(UUID tenantId, List<DomainEvent> events);
}
