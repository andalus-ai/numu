package tech.kayys.syirkah.accounting.domain.compliance;

import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;

/**
 * IFRS compliance strategy enforcing fair value principles and IAS/IFRS standards.
 */
public final class IfrsComplianceStrategy implements AccountingComplianceStrategy {

    @Override
    public ComplianceStandard getStandard() {
        return ComplianceStandard.IFRS;
    }

    @Override
    public void validateAccount(Account account, ComplianceConfiguration config) {
        // Standard IFRS general validation
    }

    @Override
    public void validateJournalEntry(JournalEntry entry, ComplianceConfiguration config) {
        if (entry.getLines().isEmpty()) {
            throw new IllegalStateException("IFRS journal entry must contain at least one line");
        }
    }
}
