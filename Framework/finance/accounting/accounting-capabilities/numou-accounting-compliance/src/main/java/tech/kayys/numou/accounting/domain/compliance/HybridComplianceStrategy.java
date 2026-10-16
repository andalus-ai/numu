package tech.kayys.syirkah.accounting.domain.compliance;

import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.islamic.ShariaContractType;
import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;

/**
 * Hybrid dual-ledger strategy supporting both conventional and Islamic windows simultaneously.
 */
public final class HybridComplianceStrategy implements AccountingComplianceStrategy {

    private final ShariaComplianceStrategy shariaStrategy = new ShariaComplianceStrategy();
    private final SakIndonesiaComplianceStrategy sakStrategy = new SakIndonesiaComplianceStrategy();

    @Override
    public ComplianceStandard getStandard() {
        return ComplianceStandard.HYBRID;
    }

    @Override
    public void validateAccount(Account account, ComplianceConfiguration config) {
        sakStrategy.validateAccount(account, config);
    }

    @Override
    public void validateJournalEntry(JournalEntry entry, ComplianceConfiguration config) {
        sakStrategy.validateJournalEntry(entry, config);
        if (entry.getShariaContractType() != null && entry.getShariaContractType().isShariaContract()) {
            shariaStrategy.validateJournalEntry(entry, config);
        }
    }
}
