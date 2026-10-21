package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.HafalanLogId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record HafalanRecorded(
        UUID eventId,
        Instant occurredAt,
        HafalanLogId hafalanLogId,
        TenantId tenantId,
        SantriId santriId,
        int surah,
        int fromAyah,
        int toAyah
) implements DomainEvent {
    public HafalanRecorded(HafalanLogId hafalanLogId, TenantId tenantId, SantriId santriId, int surah, int fromAyah, int toAyah) {
        this(UUID.randomUUID(), Instant.now(), hafalanLogId, tenantId, santriId, surah, fromAyah, toAyah);
    }

    @Override
    public String eventType() {
        return "pesantren.hafalan.recorded";
    }
}
