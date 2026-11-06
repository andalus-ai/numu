package tech.kayys.numou.accounting.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.accounting.application.api.command.PostJournalEntryCommand;
import tech.kayys.numou.accounting.application.compliance.ComplianceRulePipeline;
import tech.kayys.numou.accounting.application.cqrs.CommandHandler;
import tech.kayys.numou.accounting.application.outbox.OutboxEvent;
import tech.kayys.numou.accounting.application.outbox.OutboxRepository;
import tech.kayys.numou.accounting.application.port.AccountRepository;
import tech.kayys.numou.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.numou.accounting.application.port.JournalEntryRepository;
import tech.kayys.numou.accounting.application.projection.ProjectionRegistry;
import tech.kayys.numou.accounting.application.service.AccountingComplianceEngine;
import tech.kayys.numou.accounting.domain.event.AccountingEvent;
import tech.kayys.numou.accounting.domain.identifier.AccountId;
import tech.kayys.numou.accounting.domain.identifier.JournalEntryId;
import tech.kayys.numou.accounting.domain.model.Account;
import tech.kayys.numou.accounting.domain.model.JournalEntry;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public class PostJournalEntryHandler implements CommandHandler<PostJournalEntryCommand, JournalEntryId> {
    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final OutboxRepository outboxRepository;
    private final ProjectionRegistry projectionRegistry;
    private final AccountingComplianceEngine complianceEngine;
    private final ComplianceRulePipeline rulePipeline;

    public PostJournalEntryHandler(JournalEntryRepository journalEntryRepository, AccountRepository accountRepository,
            FiscalPeriodRepository fiscalPeriodRepository, OutboxRepository outboxRepository,
            ProjectionRegistry projectionRegistry, AccountingComplianceEngine complianceEngine,
            ComplianceRulePipeline rulePipeline) {
        this.journalEntryRepository = Objects.requireNonNull(journalEntryRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.fiscalPeriodRepository = Objects.requireNonNull(fiscalPeriodRepository);
        this.outboxRepository = Objects.requireNonNull(outboxRepository);
        this.projectionRegistry = Objects.requireNonNull(projectionRegistry);
        this.complianceEngine = Objects.requireNonNull(complianceEngine);
        this.rulePipeline = Objects.requireNonNull(rulePipeline);
    }

    @Override
    public Uni<JournalEntryId> handle(PostJournalEntryCommand command) {
        JournalEntryId entryId = command.journalEntryId() != null ? command.journalEntryId() : JournalEntryId.generate();
        JournalEntry entry = new JournalEntry(entryId, command.tenantId(), command.ledgerId(),
                command.referenceNumber() != null ? command.referenceNumber()
                        : "JE-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.now(), command.description());

        for (PostJournalEntryCommand.JournalLineCommand lineCmd : command.lines()) {
            AccountId accountId = AccountId.of(lineCmd.accountId());
            Money amount = Money.of(new java.math.BigDecimal(lineCmd.amount()), lineCmd.currencyCode());
            Money debit = "DEBIT".equalsIgnoreCase(lineCmd.type()) ? amount : Money.zero(lineCmd.currencyCode());
            Money credit = "CREDIT".equalsIgnoreCase(lineCmd.type()) ? amount : Money.zero(lineCmd.currencyCode());
            entry.addLine(accountId, debit, credit, lineCmd.description());
        }

        complianceEngine.validateJournalEntry(entry);
        rulePipeline.execute(entry, complianceEngine.getConfiguration());
        entry.post(command.postedBy() != null ? command.postedBy() : "system");
        LocalDate txDate = LocalDate.ofInstant(entry.getEntryDate(), ZoneOffset.UTC);

        return fiscalPeriodRepository.findActivePeriod(txDate).chain(optPeriod -> {
            if (optPeriod.isPresent() && !optPeriod.get().isOpen()
                    && complianceEngine.getConfiguration().strictPeriodLocking()) {
                return Uni.createFrom().failure(new IllegalStateException(
                        "Cannot post entry into closed or locked fiscal period: " + optPeriod.get().getPeriodName()));
            }

            Uni<Void> applyLines = Uni.createFrom().voidItem();
            for (JournalEntry.JournalLine line : entry.getLines()) {
                applyLines = applyLines.chain(() -> accountRepository
                        .findById(command.tenantId(), command.ledgerId(), line.accountId())
                        .chain(optAccount -> {
                            if (optAccount.isEmpty()) {
                                return Uni.createFrom().failure(new IllegalArgumentException(
                                        "Account not found in tenant/ledger scope: " + line.accountId()));
                            }
                            Account account = optAccount.get();
                            complianceEngine.validateAccount(account);
                            if (line.debit().isPositive()) account.debit(line.debit());
                            if (line.credit().isPositive()) account.credit(line.credit());
                            return accountRepository.save(account);
                        }));
            }

            return applyLines.chain(() -> journalEntryRepository.save(entry)).chain(() -> {
                Uni<Void> eventChain = Uni.createFrom().voidItem();
                for (DomainEvent event : entry.pullDomainEvents()) {
                    if (event instanceof AccountingEvent accountingEvent) {
                        OutboxEvent outbox = OutboxEvent.of("JournalEntry", entryId.value().toString(),
                                accountingEvent.eventType(), accountingEvent.tenantId(),
                                accountingEvent.ledgerId(), accountingEvent.toString());
                        eventChain = eventChain.chain(() -> outboxRepository.save(outbox))
                                .chain(() -> projectionRegistry.publishToProjections(accountingEvent));
                    }
                }
                return eventChain;
            }).map(ignored -> entryId);
        });
    }
}
