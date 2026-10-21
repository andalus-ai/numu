package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.PesantrenId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record PesantrenRegistered(
        UUID eventId,
        Instant occurredAt,
        PesantrenId pesantrenId,
        TenantId tenantId,
        String name
) implements DomainEvent {
    public PesantrenRegistered(PesantrenId pesantrenId, TenantId tenantId, String name) {
        this(UUID.randomUUID(), Instant.now(), pesantrenId, tenantId, name);
    }

    @Override
    public String eventType() {
        return "pesantren.pesantren.registered";
    }
}
