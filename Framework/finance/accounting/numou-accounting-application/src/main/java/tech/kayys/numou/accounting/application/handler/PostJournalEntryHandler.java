package tech.kayys.syirkah.accounting.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.compliance.ComplianceRulePipeline;
import tech.kayys.syirkah.accounting.application.cqrs.CommandHandler;
import tech.kayys.syirkah.accounting.application.outbox.OutboxEvent;
import tech.kayys.syirkah.accounting.application.outbox.OutboxRepository;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.application.projection.ProjectionRegistry;
import tech.kayys.syirkah.accounting.application.service.AccountingComplianceEngine;
import tech.kayys.syirkah.accounting.domain.event.AccountingEvent;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

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

    public PostJournalEntryHandler(
            JournalEntryRepository journalEntryRepository,
            AccountRepository accountRepository,
            FiscalPeriodRepository fiscalPeriodRepository,
            OutboxRepository outboxRepository,
            ProjectionRegistry projectionRegistry,
            AccountingComplianceEngine complianceEngine,
            ComplianceRulePipeline rulePipeline
    ) {
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
        JournalEntryId entryId = command.journalEntryId() != null 
                ? command.journalEntryId() 
                : JournalEntryId.generate();

        JournalEntry entry = new JournalEntry(
                entryId,
                TenantRef.defaultTenant(),
                LedgerId.primary(),
                command.referenceNumber() != null ? command.referenceNumber() : "JE-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.now(),
                command.description()
        );

        for (PostJournalEntryCommand.JournalLineCommand lineCmd : command.lines()) {
            AccountId accId = AccountId.of(lineCmd.accountId());
            Money amt = Money.of(new java.math.BigDecimal(lineCmd.amount()), lineCmd.currencyCode());
            Money debit = "DEBIT".equalsIgnoreCase(lineCmd.type()) ? amt : Money.zero(lineCmd.currencyCode());
            Money credit = "CREDIT".equalsIgnoreCase(lineCmd.type()) ? amt : Money.zero(lineCmd.currencyCode());
            entry.addLine(accId, debit, credit, lineCmd.description());
        }

        // Compliance & rule validation
        complianceEngine.validateJournalEntry(entry);
        rulePipeline.execute(entry, complianceEngine.getConfiguration());
        entry.post(command.postedBy() != null ? command.postedBy() : "system");

        LocalDate txDate = LocalDate.ofInstant(entry.getEntryDate(), ZoneOffset.UTC);

        return fiscalPeriodRepository.findActivePeriod(txDate)
                .chain(optPeriod -> {
                    if (optPeriod.isPresent() && !optPeriod.get().isOpen() && complianceEngine.getConfiguration().strictPeriodLocking()) {
                        return Uni.createFrom().failure(new IllegalStateException(
                                "Cannot post entry into closed or locked fiscal period: " + optPeriod.get().getPeriodName()));
                    }

                    Uni<Void> applyLines = Uni.createFrom().voidItem();
                    for (JournalEntry.JournalLine line : entry.getLines()) {
                        applyLines = applyLines.chain(() -> accountRepository.findById(line.accountId())
                                .chain(optAcc -> {
                                    if (optAcc.isEmpty()) {
                                        return Uni.createFrom().failure(new IllegalArgumentException("Account not found: " + line.accountId()));
                                    }
                                    Account acc = optAcc.get();
                                    complianceEngine.validateAccount(acc);

                                    if (line.debit().isPositive()) {
                                        acc.debit(line.debit());
                                    }
                                    if (line.credit().isPositive()) {
                                        acc.credit(line.credit());
                                    }
                                    return accountRepository.save(acc);
                                }));
                    }

                    // Save entry + outbox events + publish to read projections
                    return applyLines
                            .chain(() -> journalEntryRepository.save(entry))
                            .chain(() -> {
                                Uni<Void> eventChain = Uni.createFrom().voidItem();
                                for (DomainEvent ev : entry.pullDomainEvents()) {
                                    if (ev instanceof AccountingEvent accEv) {
                                        OutboxEvent outbox = OutboxEvent.of("JournalEntry", entryId.value().toString(), accEv.eventType(), accEv.tenantId(), accEv.ledgerId(), accEv.toString());
                                        eventChain = eventChain
                                                .chain(() -> outboxRepository.save(outbox))
                                                .chain(() -> projectionRegistry.publishToProjections(accEv));
                                    }
                                }
                                return eventChain;
                            })
                            .map(v -> entryId);
                });
    }
}
