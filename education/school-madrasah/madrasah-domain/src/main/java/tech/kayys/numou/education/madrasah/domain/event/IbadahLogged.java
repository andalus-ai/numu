package tech.kayys.numou.education.madrasah.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.madrasah.domain.IbadahLogId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record IbadahLogged(
        UUID eventId,
        Instant occurredAt,
        IbadahLogId logId,
        TenantId tenantId,
        StudentId studentId,
        LocalDate date
) implements DomainEvent {
    public IbadahLogged(IbadahLogId logId, TenantId tenantId, StudentId studentId, LocalDate date) {
        this(UUID.randomUUID(), Instant.now(), logId, tenantId, studentId, date);
    }

    @Override
    public String eventType() {
        return "madrasah.ibadah.logged";
    }
}
