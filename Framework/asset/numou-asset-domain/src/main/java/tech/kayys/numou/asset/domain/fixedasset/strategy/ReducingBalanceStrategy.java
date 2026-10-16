package tech.kayys.syirkah.asset.domain.fixedasset.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Declining / Reducing Balance depreciation: CarryingValue * (AnnualRate / 12).
 */
public final class ReducingBalanceStrategy implements DepreciationStrategy {

    @Override
    public BigDecimal calculatePeriodDepreciation(
            BigDecimal cost,
            BigDecimal accumulatedDepr,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            int currentPeriodMonth,
            Map<String, Object> parameters) {

        BigDecimal carryingValue = cost.subtract(accumulatedDepr);
        BigDecimal maxDepreciable = carryingValue.subtract(salvageValue).max(BigDecimal.ZERO);
        if (maxDepreciable.signum() <= 0) return BigDecimal.ZERO;

        BigDecimal annualRate = BigDecimal.valueOf(0.20); // Default 20%
        if (parameters != null && parameters.containsKey("annualRate")) {
            Object rateObj = parameters.get("annualRate");
            if (rateObj instanceof BigDecimal bd) annualRate = bd;
            else if (rateObj instanceof Number num) annualRate = BigDecimal.valueOf(num.doubleValue());
        }

        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
        BigDecimal amount = carryingValue.multiply(monthlyRate).setScale(4, RoundingMode.HALF_UP);
        return amount.min(maxDepreciable);
    }
}
