package tech.kayys.numou.document.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.util.List;

public interface DocumentOutboxPort {
    Uni<Void> append(String tenantId, List<DomainEvent> events);
}
