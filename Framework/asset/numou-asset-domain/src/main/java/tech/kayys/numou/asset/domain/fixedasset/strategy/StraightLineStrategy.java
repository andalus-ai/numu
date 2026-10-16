package tech.kayys.syirkah.asset.domain.fixedasset.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Straight-line depreciation: (Cost - SalvageValue) / UsefulLifeMonths.
 */
public final class StraightLineStrategy implements DepreciationStrategy {

    @Override
    public BigDecimal calculatePeriodDepreciation(
            BigDecimal cost,
            BigDecimal accumulatedDepr,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            int currentPeriodMonth,
            Map<String, Object> parameters) {

        if (usefulLifeMonths <= 0) return BigDecimal.ZERO;
        BigDecimal depreciableBase = cost.subtract(salvageValue);
        if (depreciableBase.signum() <= 0) return BigDecimal.ZERO;

        BigDecimal carryingValue = cost.subtract(accumulatedDepr);
        BigDecimal maxDepreciable = carryingValue.subtract(salvageValue).max(BigDecimal.ZERO);

        BigDecimal standardAmount = depreciableBase.divide(
                BigDecimal.valueOf(usefulLifeMonths), 4, RoundingMode.HALF_UP
        );
        return standardAmount.min(maxDepreciable);
    }
}
