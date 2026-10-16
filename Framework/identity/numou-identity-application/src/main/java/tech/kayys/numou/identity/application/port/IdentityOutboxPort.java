package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.util.List;

public interface IdentityOutboxPort {
    Uni<Void> append(TenantId tenantId, List<DomainEvent> events);
}
