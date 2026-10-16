package tech.kayys.syirkah.accounting.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.api.command.ApproveJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.cqrs.CommandHandler;
import tech.kayys.syirkah.accounting.application.outbox.OutboxEvent;
import tech.kayys.syirkah.accounting.application.outbox.OutboxRepository;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.domain.event.AccountingEvent;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.util.Objects;

public class ApproveJournalEntryHandler implements CommandHandler<ApproveJournalEntryCommand, Void> {

    private final JournalEntryRepository journalEntryRepository;
    private final OutboxRepository outboxRepository;

    public ApproveJournalEntryHandler(JournalEntryRepository journalEntryRepository, OutboxRepository outboxRepository) {
        this.journalEntryRepository = Objects.requireNonNull(journalEntryRepository);
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
    }

    @Override
    public Uni<Void> handle(ApproveJournalEntryCommand command) {
        return journalEntryRepository.findById(command.journalEntryId())
                .chain(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().failure(new IllegalArgumentException("Journal entry not found"));
                    }
                    JournalEntry entry = opt.get();
                    entry.approve(command.approvedBy());

                    Uni<Void> outboxChain = Uni.createFrom().voidItem();
                    for (DomainEvent ev : entry.pullDomainEvents()) {
                        if (ev instanceof AccountingEvent accEv) {
                            outboxChain = outboxChain.chain(() -> outboxRepository.save(
                                    OutboxEvent.of("JournalEntry", entry.id().value().toString(), accEv.eventType(), accEv.tenantId(), accEv.ledgerId(), accEv.toString())
                            ));
                        }
                    }

                    return outboxChain.chain(() -> journalEntryRepository.save(entry));
                });
    }
}
