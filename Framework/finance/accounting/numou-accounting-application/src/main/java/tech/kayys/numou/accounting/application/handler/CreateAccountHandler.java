package tech.kayys.syirkah.accounting.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.api.command.CreateAccountCommand;
import tech.kayys.syirkah.accounting.application.cqrs.CommandHandler;
import tech.kayys.syirkah.accounting.application.outbox.OutboxEvent;
import tech.kayys.syirkah.accounting.application.outbox.OutboxRepository;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.domain.event.AccountCreated;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;

import java.util.Objects;

public class CreateAccountHandler implements CommandHandler<CreateAccountCommand, AccountId> {

    private final AccountRepository accountRepository;
    private final OutboxRepository outboxRepository;

    public CreateAccountHandler(AccountRepository accountRepository, OutboxRepository outboxRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
    }

    @Override
    public Uni<AccountId> handle(CreateAccountCommand command) {
        AccountId id = AccountId.generate();
        TenantRef tenantId = command.tenantId() != null ? command.tenantId() : TenantRef.defaultTenant();
        LedgerId ledgerId = command.ledgerId() != null ? command.ledgerId() : LedgerId.primary();
        Currency cur = Currency.of(command.currencyCode() != null ? command.currencyCode() : "USD");

        Account account = new Account(id, tenantId, ledgerId, command.accountNumber(), command.name(), command.accountType(), cur);
        if (command.description() != null) {
            account.setDescription(command.description());
        }

        AccountCreated event = AccountCreated.of(tenantId, ledgerId, id, command.accountNumber(), command.name(), command.accountType(), null, null);
        OutboxEvent outbox = OutboxEvent.of("Account", id.value().toString(), event.eventType(), tenantId, ledgerId, event.toString());

        return accountRepository.save(account)
                .chain(() -> outboxRepository.save(outbox))
                .map(v -> id);
    }
}
