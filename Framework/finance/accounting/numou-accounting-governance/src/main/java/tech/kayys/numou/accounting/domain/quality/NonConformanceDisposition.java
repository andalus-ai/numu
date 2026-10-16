package tech.kayys.syirkah.accounting.domain.quality;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record NonConformanceDisposition(
        DispositionType dispositionType,
        String approvedBy,
        String justification,
        BigDecimal costImpact,
        Instant approvedAt
) {
    public NonConformanceDisposition {
        Objects.requireNonNull(dispositionType, "dispositionType must not be null");
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");
        costImpact = Objects.requireNonNullElse(costImpact, BigDecimal.ZERO);
        approvedAt = Objects.requireNonNullElse(approvedAt, Instant.now());
    }
}
