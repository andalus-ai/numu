package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record SantriStatusChanged(
        UUID eventId,
        Instant occurredAt,
        SantriId santriId,
        TenantId tenantId,
        String status
) implements DomainEvent {
    public SantriStatusChanged(SantriId santriId, TenantId tenantId, String status) {
        this(UUID.randomUUID(), Instant.now(), santriId, tenantId, status);
    }

    @Override
    public String eventType() {
        return "pesantren.santri.status-changed";
    }
}
