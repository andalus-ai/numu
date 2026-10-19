package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.event.TalentSegmentDefined;

import java.time.Instant;
import java.util.Objects;

public final class TalentSegment extends AbstractAggregateRoot<TalentSegmentId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private String description;
    private TalentSegmentStatus status;

    private TalentSegment(TalentSegmentId id, TenantId tenantId, String code, String name, String description) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.status = TalentSegmentStatus.ACTIVE;
    }

    public static TalentSegment define(TalentSegmentId id, TenantId tenantId, String code, String name, String description) {
        TalentSegment segment = new TalentSegment(id, tenantId, code, name, description);
        segment.raise(new TalentSegmentDefined(id, tenantId, code));
        return segment;
    }

    public void deactivate() {
        this.status = TalentSegmentStatus.INACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public TalentSegmentStatus getStatus() { return status; }
}
