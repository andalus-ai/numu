package tech.kayys.numou.workforce.domain.analytics;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.analytics.event.WorkforceSnapshotTaken;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class WorkforceSnapshot extends AbstractAggregateRoot<WorkforceSnapshotId> {

    private final TenantId tenantId;
    private final LocalDate snapshotDate;
    private final String scope;
    private SnapshotStatus status;

    private WorkforceSnapshot(
            WorkforceSnapshotId id,
            TenantId tenantId,
            LocalDate snapshotDate,
            String scope
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.snapshotDate = Objects.requireNonNull(snapshotDate, "snapshotDate must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.status = SnapshotStatus.IN_PROGRESS;
    }

    public static WorkforceSnapshot take(
            WorkforceSnapshotId id,
            TenantId tenantId,
            LocalDate snapshotDate,
            String scope
    ) {
        WorkforceSnapshot snapshot = new WorkforceSnapshot(id, tenantId, snapshotDate, scope);
        snapshot.raise(new WorkforceSnapshotTaken(id, tenantId, snapshotDate, scope));
        return snapshot;
    }

    public void complete() {
        this.status = SnapshotStatus.COMPLETED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void fail() {
        this.status = SnapshotStatus.FAILED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public String getScope() { return scope; }
    public SnapshotStatus getStatus() { return status; }
}
