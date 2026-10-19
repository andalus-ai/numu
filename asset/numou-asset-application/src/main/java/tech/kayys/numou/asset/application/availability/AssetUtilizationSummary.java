package tech.kayys.numou.asset.application.availability;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregated utilization for an asset over a window (ASSET-26 §19).
 *
 * <p>Metric definitions (which {@code type} counts as "utilized") stay explicit
 * here rather than being hard-coded on the aggregate: {@code byType} carries the
 * raw split so reporting can choose its own definition.</p>
 */
public record AssetUtilizationSummary(
        UUID assetId,
        Instant from,
        Instant to,
        long totalSeconds,
        BigDecimal totalQuantity,
        String unit,
        int recordCount,
        Map<String, BigDecimal> byType
) {
}