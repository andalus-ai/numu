package tech.kayys.numou.workforce.domain.benefit;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;

/**
 * Benefit aggregate root — defines an available benefit offering.
 *
 * <p>Separated from worker enrollment. Tenant-scoped.
 */
public final class Benefit extends AbstractAggregateRoot<BenefitId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private final BenefitType type;
    private BenefitStatus status;

    private Benefit(
            BenefitId id,
            TenantId tenantId,
            String code,
            String name,
            BenefitType type
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.status = BenefitStatus.ACTIVE;
    }

    public static Benefit create(
            BenefitId id,
            TenantId tenantId,
            String code,
            String name,
            BenefitType type
    ) {
        return new Benefit(id, tenantId, code, name, type);
    }

    public void rename(String newName) {
        Objects.requireNonNull(newName, "name must not be null");
        if (newName.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = newName;
    }

    public void activate() {
        this.status = BenefitStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = BenefitStatus.INACTIVE;
    }

    public TenantId tenantId() { return tenantId; }
    public String code() { return code; }
    public String name() { return name; }
    public BenefitType type() { return type; }
    public BenefitStatus status() { return status; }
    public boolean isActive() { return status == BenefitStatus.ACTIVE; }
}
