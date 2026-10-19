
package tech.kayys.numou.budget.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.Objects;

/**
 * Budget aggregate root tracking multi-dimensional allocation and commitment lifecycle.
 * available = allocated - (preEncumbered + encumbered + actualSpent)
 */
public class Budget {

    private final String budgetId;
    private final String tenantId;
    private final String ledgerId;
    private final String fiscalYear;
    private final String costCenterId;
    private final String accountId;

    private Money allocatedAmount;
    private Money preEncumberedAmount;
    private Money encumberedAmount;
    private Money actualSpentAmount;
    private BudgetStatus status;

    public Budget(
            String budgetId,
            String tenantId,
            String ledgerId,
            String fiscalYear,
            String costCenterId,
            String accountId,
            Money allocatedAmount) {
        this.budgetId = Objects.requireNonNull(budgetId, "budgetId cannot be null");
        if (budgetId.isBlank()) throw new IllegalArgumentException("budgetId cannot be blank");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId cannot be blank");
        this.ledgerId = Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
        if (ledgerId.isBlank()) throw new IllegalArgumentException("ledgerId cannot be blank");
        this.fiscalYear = Objects.requireNonNull(fiscalYear, "fiscalYear cannot be null");
        this.costCenterId = Objects.requireNonNull(costCenterId, "costCenterId cannot be null");
        if (costCenterId.isBlank()) throw new IllegalArgumentException("costCenterId cannot be blank");
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        if (accountId.isBlank()) throw new IllegalArgumentException("accountId cannot be blank");
        this.allocatedAmount = Objects.requireNonNull(allocatedAmount, "allocatedAmount cannot be null");

        tech.kayys.numou.foundation.domain.valueobject.Currency currency = allocatedAmount.currency();
        this.preEncumberedAmount = Money.zero(currency.code());
        this.encumberedAmount = Money.zero(currency.code());
        this.actualSpentAmount = Money.zero(currency.code());
        this.status = BudgetStatus.ACTIVE;
    }

    public Money availableBalance() {
        Money totalCommitted = preEncumberedAmount.add(encumberedAmount).add(actualSpentAmount);
        return allocatedAmount.subtract(totalCommitted);
    }

    public boolean canReserve(Money amount) {
        return availableBalance().amount().compareTo(amount.amount()) >= 0;
    }

    /** Step 1: Pre-encumber via Purchase Requisition. */
    public void reservePreEncumbrance(Money amount) {
        checkActive();
        if (!canReserve(amount)) {
            throw new IllegalStateException("Budget limit exceeded: Available=" + availableBalance() + ", Requested=" + amount);
        }
        this.preEncumberedAmount = this.preEncumberedAmount.add(amount);
    }

    /** Step 2: Hard encumber via Purchase Order (optionally relieving pre-encumbrance). */
    public void commitEncumbrance(Money amount, boolean relievePreEncumbrance) {
        checkActive();
        if (relievePreEncumbrance) {
            this.preEncumberedAmount = this.preEncumberedAmount.subtract(amount);
        } else if (!canReserve(amount)) {
            throw new IllegalStateException("Budget limit exceeded for encumbrance: Available=" + availableBalance() + ", Requested=" + amount);
        }
        this.encumberedAmount = this.encumberedAmount.add(amount);
    }

    /** Step 3: Relieve commitment and record actual spent upon Vendor Invoice posting. */
    public void relieveAndSpend(Money committedToRelieve, Money actualToSpend) {
        checkActive();
        this.encumberedAmount = this.encumberedAmount.subtract(committedToRelieve);
        this.actualSpentAmount = this.actualSpentAmount.add(actualToSpend);
    }

    private void checkActive() {
        if (status != BudgetStatus.ACTIVE) {
            throw new IllegalStateException("Budget is not ACTIVE: status is " + status);
        }
    }

    public String budgetId() { return budgetId; }
    public String tenantId() { return tenantId; }
    public String ledgerId() { return ledgerId; }
    public String fiscalYear() { return fiscalYear; }
    public String costCenterId() { return costCenterId; }
    public String accountId() { return accountId; }
    public Money allocatedAmount() { return allocatedAmount; }
    public Money preEncumberedAmount() { return preEncumberedAmount; }
    public Money encumberedAmount() { return encumberedAmount; }
    public Money actualSpentAmount() { return actualSpentAmount; }
    public BudgetStatus status() { return status; }
}
