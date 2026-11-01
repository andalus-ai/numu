package tech.kayys.numou.budget.adapter.memory;

import tech.kayys.numou.budget.spi.port.ActualRepository;
import tech.kayys.numou.budget.domain.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryActualRepository implements ActualRepository {
    private final Map<String, BigDecimal> actuals = new ConcurrentHashMap<>();
    @Override public void add(String tenantId, BudgetId id, String account, BudgetPeriod period, BigDecimal amount) {
        actuals.merge(key(tenantId, id, account, period), amount, BigDecimal::add);
    }
    @Override public BigDecimal totalFor(String tenantId, BudgetId id, String account, BudgetPeriod period) {
        return actuals.getOrDefault(key(tenantId, id, account, period), BigDecimal.ZERO);
    }
    private String key(String tenantId, BudgetId id, String account, BudgetPeriod period) { return tenantId + "|" + id + "|" + account + "|" + period; }
}
