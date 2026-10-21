package tech.kayys.numou.education.pesantren.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.pesantren.domain.PesantrenId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record SantriRegistered(
        UUID eventId,
        Instant occurredAt,
        SantriId santriId,
        TenantId tenantId,
        PesantrenId pesantrenId,
        String fullName
) implements DomainEvent {
    public SantriRegistered(SantriId santriId, TenantId tenantId, PesantrenId pesantrenId, String fullName) {
        this(UUID.randomUUID(), Instant.now(), santriId, tenantId, pesantrenId, fullName);
    }

    @Override
    public String eventType() {
        return "pesantren.santri.registered";
    }
}
