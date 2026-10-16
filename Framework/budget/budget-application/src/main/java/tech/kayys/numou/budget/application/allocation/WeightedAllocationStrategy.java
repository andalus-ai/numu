package tech.kayys.numou.budget.application.allocation;

import java.math.*;
import java.util.*;

public final class WeightedAllocationStrategy implements AllocationStrategy {
    @Override public Map<String, BigDecimal> allocate(BigDecimal amount, Map<String, BigDecimal> weights) {
        if (amount == null || amount.signum() < 0 || weights == null || weights.isEmpty()) throw new IllegalArgumentException("valid amount and weights are required");
        var total = weights.values().stream().peek(w -> { if (w == null || w.signum() < 0) throw new IllegalArgumentException("weights must be non-negative"); }).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) throw new IllegalArgumentException("weights sum to zero");
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        weights.forEach((target, weight) -> result.put(target, amount.multiply(weight).divide(total, MathContext.DECIMAL64)));
        return Map.copyOf(result);
    }
}
