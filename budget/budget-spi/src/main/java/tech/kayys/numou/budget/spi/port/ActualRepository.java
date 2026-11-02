package tech.kayys.numou.budget.spi.port;

import tech.kayys.numou.budget.domain.*;

import java.math.BigDecimal;

public interface ActualRepository {
    void add(String tenantId, BudgetId budgetId, String accountCode, BudgetPeriod period, BigDecimal amount);
    BigDecimal totalFor(String tenantId, BudgetId budgetId, String accountCode, BudgetPeriod period);
}
