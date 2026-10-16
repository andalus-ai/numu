package tech.kayys.numou.workforce.domain.paycomponent;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;

/**
 * PayComponent defines a configurable payroll component (earning or deduction).
 *
 * <p>Examples: BASIC_SALARY, TRANSPORT, MEAL, OVERTIME, TAX_DEDUCTION.
 * Each tenant configures their own payroll vocabulary via PayComponents.
 */
public final class PayComponent extends AbstractAggregateRoot<PayComponentId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private final PayComponentType type;
    private PayComponentStatus status;

    private PayComponent(
            PayComponentId id,
            TenantId tenantId,
            String code,
            String name,
            PayComponentType type
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        if (code.isBlank()) throw new IllegalArgumentException("code must not be blank");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.status = PayComponentStatus.ACTIVE;
    }

    public static PayComponent create(
            PayComponentId id,
            TenantId tenantId,
            String code,
            String name,
            PayComponentType type
    ) {
        return new PayComponent(id, tenantId, code, name, type);
    }

    public void rename(String newName) {
        Objects.requireNonNull(newName, "name must not be null");
        if (newName.isBlank()) throw new IllegalArgumentException("name must not be blank");
        this.name = newName;
    }

    public void activate() {
        this.status = PayComponentStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = PayComponentStatus.INACTIVE;
    }

    public TenantId tenantId() { return tenantId; }
    public String code() { return code; }
    public String name() { return name; }
    public PayComponentType type() { return type; }
    public PayComponentStatus status() { return status; }
    public boolean isActive() { return status == PayComponentStatus.ACTIVE; }
}
