package tech.kayys.numou.accounting.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.application.api.command.ReverseJournalEntryCommand;
import tech.kayys.numou.accounting.application.cqrs.CommandHandler;
import tech.kayys.numou.accounting.application.outbox.OutboxEvent;
import tech.kayys.numou.accounting.application.outbox.OutboxRepository;
import tech.kayys.numou.accounting.application.port.AccountRepository;
import tech.kayys.numou.accounting.application.port.JournalEntryRepository;
import tech.kayys.numou.accounting.application.projection.ProjectionRegistry;
import tech.kayys.numou.accounting.domain.event.AccountingEvent;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ReverseJournalEntryHandler implements CommandHandler<ReverseJournalEntryCommand, JournalEntryId> {
    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final OutboxRepository outboxRepository;
    private final ProjectionRegistry projectionRegistry;

    public ReverseJournalEntryHandler(JournalEntryRepository journalEntryRepository, AccountRepository accountRepository,
            OutboxRepository outboxRepository, ProjectionRegistry projectionRegistry) {
        this.journalEntryRepository = Objects.requireNonNull(journalEntryRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.projectionRegistry = Objects.requireNonNull(projectionRegistry);
    }

    @Override
    public Uni<JournalEntryId> handle(ReverseJournalEntryCommand command) {
        return journalEntryRepository.findById(command.tenantId(), command.ledgerId(), command.journalEntryId())
                .chain(optional -> {
                    if (optional.isEmpty()) {
                        return Uni.createFrom().failure(new IllegalArgumentException(
                                "Journal entry not found in tenant/ledger scope"));
                    }
                    JournalEntry original = optional.get();
                    JournalEntryId reversalId = JournalEntryId.generate();
                    String reversalNumber = "REV-" + original.getEntryNumber() + "-"
                            + UUID.randomUUID().toString().substring(0, 4);
                    Instant now = Instant.now();
                    JournalEntry reversal = original.createStornoReversal(reversalId, reversalNumber, now,
                            command.reversedBy(), command.reason());
                    reversal.post(command.reversedBy());
                    original.markReversed(reversalId, now, command.reversedBy());

                    Uni<Void> mutateAccounts = Uni.createFrom().voidItem();
                    for (JournalEntry.JournalLine line : reversal.getLines()) {
                        mutateAccounts = mutateAccounts.chain(() -> accountRepository
                                .findById(command.tenantId(), command.ledgerId(), line.accountId())
                                .chain(optionalAccount -> {
                                    if (optionalAccount.isEmpty()) {
                                        return Uni.createFrom().failure(new IllegalStateException(
                                                "Reversal account missing from tenant/ledger scope: " + line.accountId()));
                                    }
                                    Account account = optionalAccount.get();
                                    if (line.debit().isPositive()) account.debit(line.debit());
                                    if (line.credit().isPositive()) account.credit(line.credit());
                                    return accountRepository.save(account);
                                }));
                    }

                    return mutateAccounts.chain(() -> journalEntryRepository.save(original))
                            .chain(() -> journalEntryRepository.save(reversal))
                            .chain(() -> {
                                Uni<Void> eventChain = Uni.createFrom().voidItem();
                                for (DomainEvent event : original.pullDomainEvents()) {
                                    if (event instanceof AccountingEvent accountingEvent) {
                                        eventChain = eventChain
                                                .chain(() -> outboxRepository.save(OutboxEvent.of("JournalEntry",
                                                        reversalId.value().toString(), accountingEvent.eventType(),
                                                        accountingEvent.tenantId(), accountingEvent.ledgerId(),
                                                        accountingEvent.toString())))
                                                .chain(() -> projectionRegistry.publishToProjections(accountingEvent));
                                    }
                                }
                                return eventChain;
                            }).map(ignored -> reversalId);
                });
    }
}
