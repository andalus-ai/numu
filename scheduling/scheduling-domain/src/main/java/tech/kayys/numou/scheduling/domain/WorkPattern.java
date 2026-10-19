package tech.kayys.numou.scheduling.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.scheduling.domain.event.WorkPatternCreated;
import tech.kayys.numou.scheduling.domain.event.WorkPatternDeactivated;

import java.time.Instant;
import java.util.Objects;

public final class WorkPattern extends AbstractAggregateRoot<WorkPatternId> {

    private final TenantId tenantId;
    private String name;
    private WorkPatternType type;
    private WorkPatternStatus status;

    private WorkPattern(WorkPatternId id, TenantId tenantId, String name, WorkPatternType type) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.status = WorkPatternStatus.ACTIVE;
    }

    public static WorkPattern create(WorkPatternId id, TenantId tenantId, String name, WorkPatternType type) {
        WorkPattern wp = new WorkPattern(id, tenantId, name, type);
        wp.raise(new WorkPatternCreated(id, tenantId, name, type));
        return wp;
    }

    public void deactivate() {
        if (status == WorkPatternStatus.INACTIVE) return;
        status = WorkPatternStatus.INACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new WorkPatternDeactivated(getId()));
    }

    public TenantId getTenantId() { return tenantId; }
    public String getName() { return name; }
    public WorkPatternType getType() { return type; }
    public WorkPatternStatus getStatus() { return status; }
    public boolean isActive() { return status == WorkPatternStatus.ACTIVE; }
}
