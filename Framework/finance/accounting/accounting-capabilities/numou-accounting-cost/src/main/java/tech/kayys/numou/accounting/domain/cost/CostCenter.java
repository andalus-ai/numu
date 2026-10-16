package tech.kayys.syirkah.accounting.domain.cost;

import java.util.Objects;

/** Cost center aggregate root. */
public record CostCenter(
        CostCenterId id,
        String code,
        String name,
        CostCenterType type,
        boolean active
) {
    public CostCenter {
        Objects.requireNonNull(id);
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
    }
}
