package tech.kayys.numou.asset.domain.warranty;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ServiceContractCoverage(
        UUID id,
        String code,
        String name,
        CoverageType type,
        BigDecimal limitAmount,
        BigDecimal deductibleAmount,
        boolean active
) {
    public ServiceContractCoverage {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }
}
