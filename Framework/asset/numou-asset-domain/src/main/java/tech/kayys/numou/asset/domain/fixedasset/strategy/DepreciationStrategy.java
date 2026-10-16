package tech.kayys.syirkah.asset.domain.fixedasset.strategy;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Strategy SPI for calculating period depreciation charges across different standards.
 */
public interface DepreciationStrategy {

    /**
     * Calculates period depreciation charge.
     *
     * @param cost               initial asset acquisition cost
     * @param accumulatedDepr    accumulated depreciation up to current period
     * @param salvageValue       residual salvage value
     * @param usefulLifeMonths   total useful life in months
     * @param currentPeriodMonth month index (1 to usefulLifeMonths)
     * @param parameters         custom parameter map (e.g. rate, units produced, total estimated units)
     * @return period depreciation amount
     */
    BigDecimal calculatePeriodDepreciation(
            BigDecimal cost,
            BigDecimal accumulatedDepr,
            BigDecimal salvageValue,
            int usefulLifeMonths,
            int currentPeriodMonth,
            Map<String, Object> parameters
    );
}
