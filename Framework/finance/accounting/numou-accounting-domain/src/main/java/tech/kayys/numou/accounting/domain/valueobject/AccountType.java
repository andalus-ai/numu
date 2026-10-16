package tech.kayys.syirkah.accounting.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

public enum AccountType implements ValueObject {
    ASSET("Asset", true),
    LIABILITY("Liability", false),
    EQUITY("Equity", false),
    REVENUE("Revenue", false),
    EXPENSE("Expense", true);

    private final String description;
    private final boolean normalDebitBalance;

    AccountType(String description, boolean normalDebitBalance) {
        this.description = description;
        this.normalDebitBalance = normalDebitBalance;
    }

    public String getDescription() { return description; }
    public boolean isNormalDebitBalance() { return normalDebitBalance; }
}
