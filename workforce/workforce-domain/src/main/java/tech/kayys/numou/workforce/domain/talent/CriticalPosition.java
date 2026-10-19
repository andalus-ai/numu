package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.position.PositionId;
import tech.kayys.numou.workforce.domain.talent.event.CriticalPositionDesignated;

import java.time.Instant;
import java.util.Objects;

public final class CriticalPosition extends AbstractAggregateRoot<CriticalPositionId> {

    private final TenantId tenantId;
    private final PositionId positionId;
    private Criticality criticality;
    private String reason;
    private CriticalPositionStatus status;

    private CriticalPosition(CriticalPositionId id, TenantId tenantId, PositionId positionId, Criticality criticality, String reason) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.positionId = Objects.requireNonNull(positionId, "positionId must not be null");
        this.criticality = Objects.requireNonNull(criticality, "criticality must not be null");
        this.reason = reason;
        this.status = CriticalPositionStatus.ACTIVE;
    }

    public static CriticalPosition designate(CriticalPositionId id, TenantId tenantId, PositionId positionId, Criticality criticality, String reason) {
        CriticalPosition cp = new CriticalPosition(id, tenantId, positionId, criticality, reason);
        cp.raise(new CriticalPositionDesignated(id, tenantId, positionId, criticality));
        return cp;
    }

    public void deactivate() {
        this.status = CriticalPositionStatus.INACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public PositionId getPositionId() { return positionId; }
    public Criticality getCriticality() { return criticality; }
    public String getReason() { return reason; }
    public CriticalPositionStatus getStatus() { return status; }
}
