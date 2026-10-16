package tech.kayys.syirkah.accounting.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.api.command.PostJournalEntryCommand;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.application.port.FiscalPeriodRepository;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.domain.islamic.ShariaContractType;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public class GeneralLedgerService {

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final AccountingComplianceEngine complianceEngine;

    public GeneralLedgerService(
            AccountRepository accountRepository,
            JournalEntryRepository journalEntryRepository,
            FiscalPeriodRepository fiscalPeriodRepository,
            AccountingComplianceEngine complianceEngine
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.journalEntryRepository = Objects.requireNonNull(journalEntryRepository);
        this.fiscalPeriodRepository = Objects.requireNonNull(fiscalPeriodRepository);
        this.complianceEngine = Objects.requireNonNull(complianceEngine);
    }

    public Uni<JournalEntryId> postJournalEntry(PostJournalEntryCommand command) {
        JournalEntryId entryId = command.journalEntryId() != null 
                ? command.journalEntryId() 
                : JournalEntryId.generate();

        JournalEntry entry = new JournalEntry(
                entryId,
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

        // Validate double-entry invariant & compliance
        complianceEngine.validateJournalEntry(entry);
        entry.post();

        LocalDate txDate = LocalDate.ofInstant(entry.getEntryDate(), ZoneOffset.UTC);

        return fiscalPeriodRepository.findActivePeriod(txDate)
                .chain(optPeriod -> {
                    if (optPeriod.isPresent() && !optPeriod.get().isOpen() && complianceEngine.getConfiguration().strictPeriodLocking()) {
                        return Uni.createFrom().failure(new IllegalStateException(
                                "Cannot post entry into closed or locked fiscal period: " + optPeriod.get().getPeriodName()));
                    }

                    // Apply debit/credit to each account in parallel/sequence
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

                    return applyLines.chain(() -> journalEntryRepository.save(entry)).map(v -> entryId);
                });
    }
}
