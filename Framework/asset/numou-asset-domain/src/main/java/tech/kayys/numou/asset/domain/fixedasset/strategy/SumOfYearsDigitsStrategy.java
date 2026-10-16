package tech.kayys.syirkah.asset.domain.fixedasset.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Sum-of-the-Years'-Digits (SYD) accelerated depreciation method.
 */
public final class SumOfYearsDigitsStrategy implements DepreciationStrategy {

    @Override
    public BigDecimal calculatePeriodDepreciation(
            BigDecimal cost,
            BigDecimal accumulatedDepr,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            int currentPeriodMonth,
            Map<String, Object> parameters) {

        BigDecimal depreciableBase = cost.subtract(salvageValue);
        if (depreciableBase.signum() <= 0) return BigDecimal.ZERO;

        BigDecimal carryingValue = cost.subtract(accumulatedDepr);
        BigDecimal maxDepreciable = carryingValue.subtract(salvageValue).max(BigDecimal.ZERO);
        if (maxDepreciable.signum() <= 0) return BigDecimal.ZERO;

        int totalMonths = Math.max(1, usefulLifeMonths);
        // SYD denominator = n*(n+1)/2
        long syd = (long) totalMonths * (totalMonths + 1) / 2;
        // Remaining periods at current period = (n - currentPeriodMonth + 1)
        int remainingPeriods = Math.max(1, totalMonths - currentPeriodMonth + 1);

        BigDecimal fraction = BigDecimal.valueOf(remainingPeriods)
                .divide(BigDecimal.valueOf(syd), 6, RoundingMode.HALF_UP);

        BigDecimal amount = depreciableBase.multiply(fraction).setScale(4, RoundingMode.HALF_UP);
        return amount.min(maxDepreciable);
    }
}
