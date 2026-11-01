package tech.kayys.numou.budget.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Commitment lifecycle: OPEN -> PARTIAL -> CONSUMED or RELEASED. */
public final class BudgetCommitment {
    private final String tenantId;
    private final CommitmentId id;
    private final BudgetId budgetId;
    private final String accountCode;
    private final BudgetPeriod period;
    private final BigDecimal originalAmount;
    private final String reference;
    private BigDecimal remaining;
    private CommitmentStatus status = CommitmentStatus.OPEN;

    public BudgetCommitment(String tenantId, CommitmentId id, BudgetId budgetId, String accountCode,
                            BigDecimal amount, String reference) {
        this(tenantId, id, budgetId, accountCode, new BudgetPeriod(1, 1), amount, reference);
    }

    public BudgetCommitment(String tenantId, CommitmentId id, BudgetId budgetId, String accountCode,
                            BudgetPeriod period, BigDecimal amount, String reference) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        this.tenantId = tenantId;
        this.id = Objects.requireNonNull(id);
        this.budgetId = Objects.requireNonNull(budgetId);
        if (accountCode == null || accountCode.isBlank()) throw new IllegalArgumentException("account code must not be blank");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("commitment amount must be positive");
        if (reference == null || reference.isBlank()) throw new IllegalArgumentException("reference must not be blank");
        this.accountCode = accountCode;
        this.period = Objects.requireNonNull(period);
        this.originalAmount = amount;
        this.remaining = amount;
        this.reference = reference;
    }
    public String tenantId() { return tenantId; }
    public CommitmentId id() { return id; }
    public BudgetId budgetId() { return budgetId; }
    public String accountCode() { return accountCode; }
    public BudgetPeriod period() { return period; }
    public BigDecimal originalAmount() { return originalAmount; }
    public BigDecimal remaining() { return remaining; }
    public String reference() { return reference; }
    public CommitmentStatus status() { return status; }
    public void consume(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new BudgetViolationException("consume amount exceeds remaining commitment");
        }
        remaining = remaining.subtract(amount);
        status = remaining.signum() == 0 ? CommitmentStatus.CONSUMED : CommitmentStatus.PARTIAL;
    }
    public void release() {
        if (status == CommitmentStatus.CONSUMED || status == CommitmentStatus.RELEASED) {
            throw new BudgetViolationException("commitment is already " + status);
        }
        remaining = BigDecimal.ZERO;
        status = CommitmentStatus.RELEASED;
    }
}
