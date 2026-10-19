package tech.kayys.numou.budget.application.allocation;

import java.math.BigDecimal;
import java.util.Map;

public interface AllocationStrategy {
    Map<String, BigDecimal> allocate(BigDecimal amount, Map<String, BigDecimal> weights);
}
