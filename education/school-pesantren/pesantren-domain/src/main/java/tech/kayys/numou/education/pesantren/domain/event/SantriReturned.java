package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.LeavePermitId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record SantriReturned(
        UUID eventId,
        Instant occurredAt,
        LeavePermitId permitId,
        TenantId tenantId,
        SantriId santriId,
        int lateDays
) implements DomainEvent {
    public SantriReturned(LeavePermitId permitId, TenantId tenantId, SantriId santriId, int lateDays) {
        this(UUID.randomUUID(), Instant.now(), permitId, tenantId, santriId, lateDays);
    }

    @Override
    public String eventType() {
        return "pesantren.leave.returned";
    }
}
