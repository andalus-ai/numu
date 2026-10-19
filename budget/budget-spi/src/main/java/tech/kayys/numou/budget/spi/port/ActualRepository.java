package tech.kayys.numou.budget.spi.port;

import tech.kayys.numou.budget.domain.*;

import java.math.BigDecimal;

public interface ActualRepository {
    void add(BudgetId budgetId, String accountCode, BudgetPeriod period, BigDecimal amount);
    BigDecimal totalFor(BudgetId budgetId, String accountCode, BudgetPeriod period);
}
