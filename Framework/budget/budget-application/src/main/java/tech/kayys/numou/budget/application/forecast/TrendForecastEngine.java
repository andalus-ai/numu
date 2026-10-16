package tech.kayys.numou.budget.application.forecast;

import tech.kayys.numou.budget.domain.BudgetPeriod;
import java.math.*;
import java.util.*;

public final class TrendForecastEngine implements ForecastEngine {
    @Override public Map<BudgetPeriod, BigDecimal> forecast(List<BudgetPeriod> periods, List<BigDecimal> totals, List<BudgetPeriod> future) {
        if (periods.size() != totals.size()) throw new IllegalArgumentException("historical periods and totals must align");
        if (totals.isEmpty()) return future.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(p -> p, p -> BigDecimal.ZERO));
        BigDecimal n = BigDecimal.valueOf(totals.size()), sx = BigDecimal.ZERO, sy = BigDecimal.ZERO, sxy = BigDecimal.ZERO, sx2 = BigDecimal.ZERO;
        for (int i = 0; i < totals.size(); i++) {
            var x = BigDecimal.valueOf(i); var y = Objects.requireNonNull(totals.get(i));
            sx = sx.add(x); sy = sy.add(y); sxy = sxy.add(x.multiply(y)); sx2 = sx2.add(x.multiply(x));
        }
        var denominator = n.multiply(sx2).subtract(sx.multiply(sx));
        var slope = denominator.signum() == 0 ? BigDecimal.ZERO : n.multiply(sxy).subtract(sx.multiply(sy)).divide(denominator, MathContext.DECIMAL64);
        var intercept = sy.subtract(slope.multiply(sx)).divide(n, MathContext.DECIMAL64);
        Map<BudgetPeriod, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < future.size(); i++) result.put(future.get(i), intercept.add(slope.multiply(BigDecimal.valueOf(totals.size() + i))));
        return Map.copyOf(result);
    }
}
