package tech.kayys.numou.budget.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Versioned planning aggregate extracted from the budgeting-platform draft.
 * It deliberately owns planning data only; journal posting remains in the ledger.
 */
public final class PlanningBudget {
    private final String tenantId;
    private final BudgetId id;
    private final String code;
    private final int fiscalYear;
    private final List<BudgetVersion> versions = new ArrayList<>();
    private PlanningBudgetStatus status = PlanningBudgetStatus.DRAFT;

    public PlanningBudget(String tenantId, BudgetId id, String code, int fiscalYear) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        this.tenantId = tenantId;
        this.id = Objects.requireNonNull(id);
        if (code == null || code.isBlank()) throw new IllegalArgumentException("budget code must not be blank");
        if (fiscalYear < 1900 || fiscalYear > 9999) throw new IllegalArgumentException("invalid fiscal year");
        this.code = code;
        this.fiscalYear = fiscalYear;
        versions.add(new BudgetVersion(1, List.of()));
    }
    public String tenantId() { return tenantId; }
    public BudgetId id() { return id; }
    public String code() { return code; }
    public int fiscalYear() { return fiscalYear; }
    public PlanningBudgetStatus status() { return status; }
    public List<BudgetVersion> versions() { return List.copyOf(versions); }
    public BudgetVersion currentVersion() { return versions.get(versions.size() - 1); }
    public BudgetVersion activeVersion() { return versions.get(versions.size() - 1); }
    public void setLine(String accountCode, Map<String, String> dimensions, BudgetPeriod period, BigDecimal amount) {
        if (status != PlanningBudgetStatus.DRAFT && status != PlanningBudgetStatus.REVIEW) {
            throw new BudgetViolationException("budget lines can only be edited in DRAFT or REVIEW");
        }
        currentVersion().addOrReplace(new BudgetLine(UUID.randomUUID().toString(), accountCode,
                dimensions, period, amount, BudgetLineStatus.ACTIVE));
    }
    public void submitForReview() { transition(PlanningBudgetStatus.DRAFT, PlanningBudgetStatus.REVIEW); }
    public void approve() { transition(PlanningBudgetStatus.REVIEW, PlanningBudgetStatus.APPROVED); }
    public void activate() { transition(PlanningBudgetStatus.APPROVED, PlanningBudgetStatus.ACTIVE); }
    public void close() {
        if (status == PlanningBudgetStatus.CLOSED) throw new BudgetViolationException("budget is already closed");
        status = PlanningBudgetStatus.CLOSED;
    }
    public int createVersion() {
        if (status != PlanningBudgetStatus.APPROVED && status != PlanningBudgetStatus.ACTIVE) {
            throw new BudgetViolationException("new versions require APPROVED or ACTIVE budget");
        }
        int next = versions.size() + 1;
        versions.add(currentVersion().copyAs(next));
        return next;
    }
    private void transition(PlanningBudgetStatus expected, PlanningBudgetStatus next) {
        if (status != expected) throw new BudgetViolationException("expected " + expected + " but was " + status);
        status = next;
    }
}
