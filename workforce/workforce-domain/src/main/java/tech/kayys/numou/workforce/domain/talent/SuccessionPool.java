package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.event.SuccessionPoolCreated;

import java.time.Instant;
import java.util.Objects;

public final class SuccessionPool extends AbstractAggregateRoot<SuccessionPoolId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private String description;
    private SuccessionPoolStatus status;

    private SuccessionPool(SuccessionPoolId id, TenantId tenantId, String code, String name, String description) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.status = SuccessionPoolStatus.ACTIVE;
    }

    public static SuccessionPool create(SuccessionPoolId id, TenantId tenantId, String code, String name, String description) {
        SuccessionPool pool = new SuccessionPool(id, tenantId, code, name, description);
        pool.raise(new SuccessionPoolCreated(id, tenantId, code));
        return pool;
    }

    public void deactivate() {
        this.status = SuccessionPoolStatus.INACTIVE;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public SuccessionPoolStatus getStatus() { return status; }
}
