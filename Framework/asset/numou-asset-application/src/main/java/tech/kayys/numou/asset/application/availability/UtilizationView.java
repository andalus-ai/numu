package tech.kayys.numou.asset.application.availability;

import java.math.BigDecimal;

/**
 * Minimal view over a recorded utilization observation.
 */
public record UtilizationView(
        java.util.UUID id,
        String type,
        BigDecimal quantity,
        String unit,
        java.time.Instant startsAt,
        java.time.Instant endsAt
) {
}