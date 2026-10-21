package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.LeavePermitId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record LeavePermitRequested(
        UUID eventId,
        Instant occurredAt,
        LeavePermitId permitId,
        TenantId tenantId,
        SantriId santriId,
        LocalDate from,
        LocalDate to
) implements DomainEvent {
    public LeavePermitRequested(LeavePermitId permitId, TenantId tenantId, SantriId santriId, LocalDate from, LocalDate to) {
        this(UUID.randomUUID(), Instant.now(), permitId, tenantId, santriId, from, to);
    }

    @Override
    public String eventType() {
        return "pesantren.leave.requested";
    }
}
