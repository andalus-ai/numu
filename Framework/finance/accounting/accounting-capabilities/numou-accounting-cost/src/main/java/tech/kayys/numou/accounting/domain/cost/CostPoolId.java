package tech.kayys.syirkah.accounting.domain.cost;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of an overhead/shared cost pool. */
public record CostPoolId(String value) {
    public CostPoolId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("CostPoolId must not be blank");
    }
    public static CostPoolId generate() { return new CostPoolId(UUID.randomUUID().toString()); }
    public static CostPoolId of(String v) { return new CostPoolId(v); }
}
