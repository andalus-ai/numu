package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.DisciplineRecordId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record DisciplineRecorded(
        UUID eventId,
        Instant occurredAt,
        DisciplineRecordId recordId,
        TenantId tenantId,
        SantriId santriId,
        int points
) implements DomainEvent {
    public DisciplineRecorded(DisciplineRecordId recordId, TenantId tenantId, SantriId santriId, int points) {
        this(UUID.randomUUID(), Instant.now(), recordId, tenantId, santriId, points);
    }

    @Override
    public String eventType() {
        return "pesantren.discipline.recorded";
    }
}
