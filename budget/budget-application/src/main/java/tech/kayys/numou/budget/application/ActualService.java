package tech.kayys.numou.budget.application;

import tech.kayys.numou.budget.spi.port.ActualRepository;
import tech.kayys.numou.budget.domain.*;
import java.math.BigDecimal;
import java.util.Objects;

public final class ActualService {
    private final ActualRepository actuals;
    public ActualService(ActualRepository actuals) { this.actuals = Objects.requireNonNull(actuals); }
    public void record(BudgetId budgetId, String accountCode, BudgetPeriod period, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("actual must not be negative");
        actuals.add(budgetId, accountCode, period, amount);
    }
}
