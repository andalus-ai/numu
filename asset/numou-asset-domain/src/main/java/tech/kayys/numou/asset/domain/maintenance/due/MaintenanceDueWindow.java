package tech.kayys.numou.asset.domain.maintenance.due;

import java.math.BigDecimal;
import java.time.Instant;

/** Tolerance/grace window around a due point (ASSET-22 §7/§13). */
public record MaintenanceDueWindow(
        Instant earliestDate,
        Instant dueDate,
        Instant latestDate,
        BigDecimal earliestMeter,
        BigDecimal dueMeter,
        BigDecimal latestMeter
) {}
