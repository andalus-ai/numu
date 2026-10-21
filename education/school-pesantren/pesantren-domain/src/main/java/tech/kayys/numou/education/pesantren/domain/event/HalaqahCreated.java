package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.HalaqahId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record HalaqahCreated(
        UUID eventId,
        Instant occurredAt,
        HalaqahId halaqahId,
        TenantId tenantId,
        String name
) implements DomainEvent {
    public HalaqahCreated(HalaqahId halaqahId, TenantId tenantId, String name) {
        this(UUID.randomUUID(), Instant.now(), halaqahId, tenantId, name);
    }

    @Override
    public String eventType() {
        return "pesantren.halaqah.created";
    }
}
