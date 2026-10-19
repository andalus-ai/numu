package tech.kayys.numou.asset.domain.warranty;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Coverage child of a warranty (ASSET-23). Component coverage points at the component
 * asset id via the COMPONENT_OF relation; no component master is duplicated here.
 */
public record WarrantyCoverage(
        UUID id,
        String coverageCode,
        String name,
        String description,
        CoverageType type,
        CoverageScope scope,
        UUID coveredAssetId,
        BigDecimal limitAmount,
        BigDecimal deductibleAmount,
        boolean active
) {
    public WarrantyCoverage {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(coverageCode, "coverageCode cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(scope, "scope cannot be null");
        if (coverageCode.isBlank()) throw new IllegalArgumentException("coverageCode cannot be blank");
    }
}
