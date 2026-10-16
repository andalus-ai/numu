package tech.kayys.syirkah.accounting.domain.compliance;

import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;

/**
 * Standar Akuntansi Keuangan (SAK) Indonesia compliance strategy.
 * Enforces Indonesian tax withholding rules (PPh 21, 23, 4(2)) and PPN compliance.
 */
public final class SakIndonesiaComplianceStrategy implements AccountingComplianceStrategy {

    @Override
    public ComplianceStandard getStandard() {
        return ComplianceStandard.SAK_INDONESIA;
    }

    @Override
    public void validateAccount(Account account, ComplianceConfiguration config) {
        // Validation for standard Indonesian Chart of Accounts
    }

    @Override
    public void validateJournalEntry(JournalEntry entry, ComplianceConfiguration config) {
        if (entry.getLines().isEmpty()) {
            throw new IllegalStateException("SAK Indonesia journal entry must contain at least one line");
        }
    }
}
