package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;
import java.util.UUID;

public record AuditPlanId(String value) {
    public AuditPlanId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("AuditPlanId must not be blank");
    }
    public static AuditPlanId newId() {
        return new AuditPlanId(UUID.randomUUID().toString());
    }
    public static AuditPlanId of(String value) {
        return new AuditPlanId(value);
    }
}
