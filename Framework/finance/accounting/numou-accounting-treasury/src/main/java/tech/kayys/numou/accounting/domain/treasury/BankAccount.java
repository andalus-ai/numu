
package tech.kayys.syirkah.accounting.domain.treasury;

import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;

import java.util.Objects;

public class BankAccount {

    private final String bankAccountId;
    private final TenantRef tenantId;
    private final LedgerId ledgerId;
    private final AccountId generalLedgerAccountId;
    private final String bankName;
    private final String accountNumber;
    private final String iban;
    private final Currency currency;
    private boolean active;

    public BankAccount(
            String bankAccountId,
            TenantRef tenantId,
            LedgerId ledgerId,
            AccountId generalLedgerAccountId,
            String bankName,
            String accountNumber,
            String iban,
            Currency currency) {
        this.bankAccountId = Objects.requireNonNull(bankAccountId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.ledgerId = Objects.requireNonNull(ledgerId);
        this.generalLedgerAccountId = Objects.requireNonNull(generalLedgerAccountId);
        this.bankName = Objects.requireNonNull(bankName);
        this.accountNumber = Objects.requireNonNull(accountNumber);
        this.iban = iban;
        this.currency = Objects.requireNonNull(currency);
        this.active = true;
    }

    public String bankAccountId() { return bankAccountId; }
    public TenantRef tenantId() { return tenantId; }
    public LedgerId ledgerId() { return ledgerId; }
    public AccountId generalLedgerAccountId() { return generalLedgerAccountId; }
    public String bankName() { return bankName; }
    public String accountNumber() { return accountNumber; }
    public String iban() { return iban; }
    public Currency currency() { return currency; }
    public boolean isActive() { return active; }
}
