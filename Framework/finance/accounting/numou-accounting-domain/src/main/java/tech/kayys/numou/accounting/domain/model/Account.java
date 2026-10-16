package tech.kayys.syirkah.accounting.domain.model;

import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerAware;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantAware;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountStatus;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountType;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.util.Objects;

public final class Account extends AbstractAggregateRoot<AccountId> implements TenantAware, LedgerAware {
    private final AccountId id;
    private final TenantRef tenantId;
    private final LedgerId ledgerId;
    private String accountNumber;
    private String name;
    private String description;
    private AccountType accountType;
    private AccountStatus status;
    private Money currentBalance;
    private AccountId parentAccountId;
    private boolean active;
    private boolean shariaCompliant;

    public Account(
            AccountId id,
            TenantRef tenantId,
            LedgerId ledgerId,
            String accountNumber,
            String name,
            AccountType accountType,
            Currency currency
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.tenantId = Objects.requireNonNull(tenantId != null ? tenantId : TenantRef.defaultTenant(), "tenantId cannot be null");
        this.ledgerId = Objects.requireNonNull(ledgerId != null ? ledgerId : LedgerId.primary(), "ledgerId cannot be null");
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.accountType = Objects.requireNonNull(accountType, "accountType cannot be null");
        this.status = AccountStatus.ACTIVE;
        this.currentBalance = Money.zero(currency);
        this.active = true;
        this.shariaCompliant = true;
    }

    public Account(AccountId id, String accountNumber, String name, AccountType accountType, Currency currency) {
        this(id, TenantRef.defaultTenant(), LedgerId.primary(), accountNumber, name, accountType, currency);
    }

    @Override
    public AccountId id() { return id; }
    public AccountId getId() { return id; }

    @Override
    public TenantRef tenantId() { return tenantId; }

    @Override
    public LedgerId ledgerId() { return ledgerId; }

    public String getAccountNumber() { return accountNumber; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AccountType getAccountType() { return accountType; }
    public AccountStatus getStatus() { return status; }
    public Money getCurrentBalance() { return currentBalance; }
    public AccountId getParentAccountId() { return parentAccountId; }
    public void setParentAccountId(AccountId parentAccountId) { this.parentAccountId = parentAccountId; }
    public boolean isActive() { return active; }
    public boolean isShariaCompliant() { return shariaCompliant; }
    public void setShariaCompliant(boolean shariaCompliant) { this.shariaCompliant = shariaCompliant; }

    public void deactivate() {
        this.active = false;
        this.status = AccountStatus.INACTIVE;
    }

    public void debit(Money amount) {
        Objects.requireNonNull(amount, "debit amount cannot be null");
        if (accountType.isNormalDebitBalance()) {
            this.currentBalance = this.currentBalance.add(amount);
        } else {
            this.currentBalance = this.currentBalance.subtract(amount);
        }
    }

    public void credit(Money amount) {
        Objects.requireNonNull(amount, "credit amount cannot be null");
        if (accountType.isNormalDebitBalance()) {
            this.currentBalance = this.currentBalance.subtract(amount);
        } else {
            this.currentBalance = this.currentBalance.add(amount);
        }
    }
}
