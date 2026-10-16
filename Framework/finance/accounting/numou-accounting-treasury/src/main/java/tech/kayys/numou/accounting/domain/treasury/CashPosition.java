
package tech.kayys.syirkah.accounting.domain.treasury;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.Objects;

public class CashPosition {

    private final String positionId;
    private final String bankAccountId;
    private final LocalDate positionDate;
    private Money openingBalance;
    private Money totalInflows;
    private Money totalOutflows;

    public CashPosition(String positionId, String bankAccountId, LocalDate positionDate, Money openingBalance) {
        this.positionId = Objects.requireNonNull(positionId);
        this.bankAccountId = Objects.requireNonNull(bankAccountId);
        this.positionDate = Objects.requireNonNull(positionDate);
        this.openingBalance = Objects.requireNonNull(openingBalance);
        this.totalInflows = Money.zero(openingBalance.currency().code());
        this.totalOutflows = Money.zero(openingBalance.currency().code());
    }

    public void recordInflow(Money amount) {
        this.totalInflows = this.totalInflows.add(amount);
    }

    public void recordOutflow(Money amount) {
        this.totalOutflows = this.totalOutflows.add(amount);
    }

    public Money closingBalance() {
        return openingBalance.add(totalInflows).subtract(totalOutflows);
    }

    public String positionId() { return positionId; }
    public String bankAccountId() { return bankAccountId; }
    public LocalDate positionDate() { return positionDate; }
    public Money openingBalance() { return openingBalance; }
    public Money totalInflows() { return totalInflows; }
    public Money totalOutflows() { return totalOutflows; }
}
