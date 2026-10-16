package tech.kayys.syirkah.reservation.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.util.List;
import java.util.UUID;

public interface DomainEventSink {
    Uni<Void> publish(UUID tenantId, List<DomainEvent> events);
}
