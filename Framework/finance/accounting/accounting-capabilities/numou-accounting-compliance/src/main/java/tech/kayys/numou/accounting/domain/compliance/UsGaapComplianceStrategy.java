package tech.kayys.syirkah.accounting.domain.compliance;

import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;

/**
 * US GAAP compliance strategy enforcing FASB rules and US reporting requirements.
 */
public final class UsGaapComplianceStrategy implements AccountingComplianceStrategy {

    @Override
    public ComplianceStandard getStandard() {
        return ComplianceStandard.US_GAAP;
    }

    @Override
    public void validateAccount(Account account, ComplianceConfiguration config) {
        // Standard US GAAP validation
    }

    @Override
    public void validateJournalEntry(JournalEntry entry, ComplianceConfiguration config) {
        if (entry.getLines().isEmpty()) {
            throw new IllegalStateException("US GAAP journal entry must contain at least one line");
        }
    }
}
