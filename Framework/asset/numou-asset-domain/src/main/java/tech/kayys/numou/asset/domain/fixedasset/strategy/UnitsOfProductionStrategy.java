package tech.kayys.syirkah.asset.domain.fixedasset.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Units of Production depreciation: (Cost - SalvageValue) * (UnitsProducedThisPeriod / TotalEstimatedUnits).
 */
public final class UnitsOfProductionStrategy implements DepreciationStrategy {

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

        BigDecimal unitsProduced = BigDecimal.ZERO;
        BigDecimal totalUnits = BigDecimal.valueOf(100_000); // Default total estimated units

        if (parameters != null) {
            if (parameters.containsKey("unitsProduced")) {
                Object up = parameters.get("unitsProduced");
                if (up instanceof BigDecimal bd) unitsProduced = bd;
                else if (up instanceof Number n) unitsProduced = BigDecimal.valueOf(n.doubleValue());
            }
            if (parameters.containsKey("totalEstimatedUnits")) {
                Object tu = parameters.get("totalEstimatedUnits");
                if (tu instanceof BigDecimal bd) totalUnits = bd;
                else if (tu instanceof Number n) totalUnits = BigDecimal.valueOf(n.doubleValue());
            }
        }

        if (totalUnits.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal ratePerUnit = depreciableBase.divide(totalUnits, 6, RoundingMode.HALF_UP);
        BigDecimal amount = unitsProduced.multiply(ratePerUnit).setScale(4, RoundingMode.HALF_UP);
        return amount.min(maxDepreciable);
    }
}
