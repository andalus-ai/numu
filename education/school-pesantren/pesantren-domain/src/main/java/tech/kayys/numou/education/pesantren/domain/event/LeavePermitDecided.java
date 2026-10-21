package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.LeavePermitId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record LeavePermitDecided(
        UUID eventId,
        Instant occurredAt,
        LeavePermitId permitId,
        TenantId tenantId,
        boolean approved
) implements DomainEvent {
    public LeavePermitDecided(LeavePermitId permitId, TenantId tenantId, boolean approved) {
        this(UUID.randomUUID(), Instant.now(), permitId, tenantId, approved);
    }

    @Override
    public String eventType() {
        return "pesantren.leave.decided";
    }
}
