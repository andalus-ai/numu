package tech.kayys.syirkah.accounting.application.api.command;

import tech.kayys.syirkah.accounting.application.cqrs.Command;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.accounting.domain.valueobject.AccountType;

import java.util.Objects;

public record CreateAccountCommand(
        TenantRef tenantId,
        LedgerId ledgerId,
        String accountNumber,
        String name,
        String description,
        AccountType accountType,
        String currencyCode
) implements Command {
    public CreateAccountCommand {
        Objects.requireNonNull(accountNumber, "accountNumber cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(accountType, "accountType cannot be null");
    }
}
