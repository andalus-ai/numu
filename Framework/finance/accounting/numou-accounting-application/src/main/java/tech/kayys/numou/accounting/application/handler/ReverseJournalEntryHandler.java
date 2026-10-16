package tech.kayys.syirkah.accounting.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.api.command.ReverseJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.cqrs.CommandHandler;
import tech.kayys.syirkah.accounting.application.outbox.OutboxEvent;
import tech.kayys.syirkah.accounting.application.outbox.OutboxRepository;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.application.projection.ProjectionRegistry;
import tech.kayys.syirkah.accounting.domain.event.AccountingEvent;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ReverseJournalEntryHandler implements CommandHandler<ReverseJournalEntryCommand, JournalEntryId> {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final OutboxRepository outboxRepository;
    private final ProjectionRegistry projectionRegistry;

    public ReverseJournalEntryHandler(
            JournalEntryRepository journalEntryRepository,
            AccountRepository accountRepository,
            OutboxRepository outboxRepository,
            ProjectionRegistry projectionRegistry
    ) {
        this.journalEntryRepository = Objects.requireNonNull(journalEntryRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.projectionRegistry = Objects.requireNonNull(projectionRegistry);
    }

    @Override
    public Uni<JournalEntryId> handle(ReverseJournalEntryCommand command) {
        return journalEntryRepository.findById(command.journalEntryId())
                .chain(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(new IllegalArgumentException("Journal entry not found"));
                    }
                    JournalEntry orig = opt.get();
                    JournalEntryId revId = JournalEntryId.generate();
                    String revNum = "REV-" + orig.getEntryNumber() + "-" + UUID.randomUUID().toString().substring(0, 4);

                    JournalEntry reversal = orig.createStornoReversal(revId, revNum, Instant.now(), command.reversedBy(), command.reason());
                    reversal.post(command.reversedBy());
                    orig.markReversed(revId, Instant.now(), command.reversedBy());

                    Uni<Void> mutate = Uni.createFrom().voidItem();
                    for (JournalEntry.JournalLine line : reversal.getLines()) {
                        mutate = mutate.chain(() -> accountRepository.findById(line.accountId())
                                .chain(optAcc -> {
                                    if (optAcc.isEmpty()) return Uni.createFrom().voidItem();
                                    Account acc = optAcc.get();
                                    if (line.debit().isPositive()) {
                                        acc.debit(line.debit());
                                    }
                                    if (line.credit().isPositive()) {
                                        acc.credit(line.credit());
                                    }
                                    return accountRepository.save(acc);
                                }));
                    }

                    return mutate
                            .chain(() -> journalEntryRepository.save(orig))
                            .chain(() -> journalEntryRepository.save(reversal))
                            .chain(() -> {
                                Uni<Void> evChain = Uni.createFrom().voidItem();
                                for (DomainEvent ev : orig.pullDomainEvents()) {
                                    if (ev instanceof AccountingEvent accEv) {
                                        evChain = evChain
                                                .chain(() -> outboxRepository.save(OutboxEvent.of("JournalEntry", revId.value().toString(), accEv.eventType(), accEv.tenantId(), accEv.ledgerId(), accEv.toString())))
                                                .chain(() -> projectionRegistry.publishToProjections(accEv));
                                    }
                                }
                                return evChain;
                            })
                            .map(v -> revId);
                });
    }
}
