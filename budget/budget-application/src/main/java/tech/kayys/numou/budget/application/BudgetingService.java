package tech.kayys.numou.budget.application;

import tech.kayys.numou.budget.application.allocation.*;
import tech.kayys.numou.budget.application.*;
import tech.kayys.numou.budget.application.control.*;
import tech.kayys.numou.budget.application.forecast.*;
import tech.kayys.numou.budget.spi.port.*;
import tech.kayys.numou.budget.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for the budgeting application boundary.
 * Business responsibilities live in focused services and policy components.
 */
public final class BudgetingService {
    private final BudgetLifecycleService lifecycle;
    private final CommitmentService commitmentService;
    private final ActualService actualService;
    private final BudgetControlEngine control;
    private final ForecastEngine forecast;
    private final AllocationStrategy allocation;

    public BudgetingService(BudgetRepository budgets, CommitmentRepository commitments, ActualRepository actuals) {
        this.lifecycle = new BudgetLifecycleService(budgets);
        this.control = new BudgetControlEngine(budgets, commitments, actuals);
        this.commitmentService = new CommitmentService(commitments, control);
        this.actualService = new ActualService(actuals);
        this.forecast = new TrendForecastEngine();
        this.allocation = new WeightedAllocationStrategy();
    }
    public PlanningBudget create(String tenantId, BudgetId id, String code, int fiscalYear) { return lifecycle.create(tenantId, id, code, fiscalYear); }
    public PlanningBudget require(String tenantId, BudgetId id) { return lifecycle.require(tenantId, id); }
    public BudgetCommitment commit(String tenantId, CommitmentId id, BudgetId budgetId, String accountCode,
                                   BudgetPeriod period, BigDecimal amount, String reference) {
        return commit(tenantId, id, budgetId, accountCode, Map.of(), period, amount, reference);
    }
    public BudgetCommitment commit(String tenantId, CommitmentId id, BudgetId budgetId, String accountCode,
                                   Map<String, String> dimensions, BudgetPeriod period,
                                   BigDecimal amount, String reference) {
        return commitmentService.create(tenantId, id, budgetId, accountCode, dimensions, period, amount, reference);
    }
    public BudgetCommitment consume(String tenantId, CommitmentId id, BigDecimal amount) { return commitmentService.consume(tenantId, id, amount); }
    public BudgetCommitment release(String tenantId, CommitmentId id) { return commitmentService.release(tenantId, id); }
    public void recordActual(String tenantId, BudgetId id, String account, BudgetPeriod period, BigDecimal amount) {
        lifecycle.require(tenantId, id); actualService.record(tenantId, id, account, period, amount);
    }
    public Availability checkAvailability(String tenantId, BudgetId id, String account, Map<String, String> dimensions,
                                           BudgetPeriod period, BigDecimal requested) {
        return control.check(tenantId, id, account, dimensions, period, requested);
    }
    public Map<BudgetPeriod, BigDecimal> forecast(List<BudgetPeriod> periods, List<BigDecimal> totals, List<BudgetPeriod> future) {
        return forecast.forecast(periods, totals, future);
    }
    public Map<String, BigDecimal> allocate(BigDecimal amount, Map<String, BigDecimal> weights) {
        return allocation.allocate(amount, weights);
    }
}
