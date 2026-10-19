package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.List;

public interface IdentityOutboxPort {
    Uni<Void> append(TenantId tenantId, List<DomainEvent> events);
}
