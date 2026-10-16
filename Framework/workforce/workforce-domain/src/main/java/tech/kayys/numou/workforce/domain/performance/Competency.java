package tech.kayys.numou.workforce.domain.performance;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.performance.event.CompetencyCreated;

import java.time.Instant;
import java.util.Objects;

public final class Competency extends AbstractAggregateRoot<CompetencyId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private String description;
    private CompetencyStatus status;

    private Competency(CompetencyId id, TenantId tenantId, String code, String name, String description) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.status = CompetencyStatus.ACTIVE;
    }

    public static Competency create(CompetencyId id, TenantId tenantId, String code, String name, String description) {
        Competency competency = new Competency(id, tenantId, code, name, description);
        competency.raise(new CompetencyCreated(id, tenantId, code));
        return competency;
    }

    public void deactivate() {
        this.status = CompetencyStatus.INACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.status = CompetencyStatus.ACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public CompetencyStatus getStatus() { return status; }
}
