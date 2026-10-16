package tech.kayys.syirkah.accounting.domain.cost;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a cost center. */
public record CostCenterId(String value) {
    public CostCenterId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("CostCenterId must not be blank");
    }
    public static CostCenterId generate() { return new CostCenterId(UUID.randomUUID().toString()); }
    public static CostCenterId of(String v) { return new CostCenterId(v); }
}
