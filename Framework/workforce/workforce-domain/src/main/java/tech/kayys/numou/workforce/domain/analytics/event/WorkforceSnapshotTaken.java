package tech.kayys.numou.workforce.domain.analytics.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.WorkforceSnapshotId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkforceSnapshotTaken(
        UUID eventId,
        Instant occurredAt,
        WorkforceSnapshotId id,
        TenantId tenantId,
        LocalDate snapshotDate,
        String scope
) implements DomainEvent {
    public WorkforceSnapshotTaken(WorkforceSnapshotId id, TenantId tenantId, LocalDate snapshotDate, String scope) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, snapshotDate, scope);
    }
    @Override public String eventType() { return "workforce.analytics.snapshot.taken"; }
}
