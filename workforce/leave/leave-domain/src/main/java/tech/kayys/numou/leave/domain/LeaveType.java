package tech.kayys.numou.leave.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.leave.domain.event.LeaveTypeActivated;
import tech.kayys.numou.leave.domain.event.LeaveTypeCreated;
import tech.kayys.numou.leave.domain.event.LeaveTypeDeactivated;

import java.time.Instant;
import java.util.Objects;

public final class LeaveType extends AbstractAggregateRoot<LeaveTypeId> {

    private final TenantId tenantId;
    private String code;
    private String name;
    private LeaveTypeStatus status;

    private LeaveType(LeaveTypeId id, TenantId tenantId, String code, String name) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.status = LeaveTypeStatus.ACTIVE;
    }

    public static LeaveType create(LeaveTypeId id, TenantId tenantId, String code, String name) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Leave type code is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Leave type name is required");
        LeaveType lt = new LeaveType(id, tenantId, code.trim(), name.trim());
        lt.raise(new LeaveTypeCreated(id, tenantId, lt.getCode(), lt.getName()));
        return lt;
    }

    public void rename(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        this.name = name.trim();
        incrementVersion();
        updatedAt = Instant.now();
    }

    public void activate() {
        if (this.status == LeaveTypeStatus.ACTIVE) return;
        this.status = LeaveTypeStatus.ACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new LeaveTypeActivated(getId()));
    }

    public void deactivate() {
        if (this.status == LeaveTypeStatus.INACTIVE) return;
        this.status = LeaveTypeStatus.INACTIVE;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new LeaveTypeDeactivated(getId()));
    }

    public boolean isActive() { return status == LeaveTypeStatus.ACTIVE; }
    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LeaveTypeStatus getStatus() { return status; }
}
