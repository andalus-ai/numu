package tech.kayys.syirkah.accounting.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.accounting.application.port.AccountRepository;
import tech.kayys.syirkah.accounting.application.port.JournalEntryRepository;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class JournalEntryReversalService {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;

    public JournalEntryReversalService(JournalEntryRepository journalEntryRepository, AccountRepository accountRepository) {
        this.journalEntryRepository = Objects.requireNonNull(journalEntryRepository);
        this.accountRepository = Objects.requireNonNull(accountRepository);
    }

    public Uni<JournalEntryId> reverseJournalEntry(JournalEntryId entryId, String reversedBy, String reason) {
        return journalEntryRepository.findById(entryId)
                .chain(optEntry -> {
                    if (optEntry.isEmpty()) {
                        return Uni.createFrom().failure(new IllegalArgumentException("Journal entry not found: " + entryId));
                    }
                    JournalEntry original = optEntry.get();
                    JournalEntryId reversalId = JournalEntryId.generate();
                    String reversalNumber = "REV-" + original.getEntryNumber() + "-" + UUID.randomUUID().toString().substring(0, 4);

                    JournalEntry reversal = original.createStornoReversal(reversalId, reversalNumber, Instant.now(), reversedBy, reason);
                    reversal.post();
                    original.markReversed(reversalId, Instant.now(), reversedBy);

                    // Inverse balance mutations
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
                            .chain(() -> journalEntryRepository.save(original))
                            .chain(() -> journalEntryRepository.save(reversal))
                            .map(v -> reversalId);
                });
    }
}
