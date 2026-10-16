package tech.kayys.syirkah.accounting.domain.compliance;

import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;

/**
 * SPI for accounting compliance standards (IFRS, US GAAP, SAK Indonesia, SAK Syariah, Hybrid).
 */
public interface AccountingComplianceStrategy {

    /** Returns the compliance standard this strategy handles. */
    ComplianceStandard getStandard();

    /** Validates whether an account is compliant under this standard. */
    void validateAccount(Account account, ComplianceConfiguration config);

    /** Validates whether a journal entry satisfies all rules under this standard. */
    void validateJournalEntry(JournalEntry entry, ComplianceConfiguration config);
}
