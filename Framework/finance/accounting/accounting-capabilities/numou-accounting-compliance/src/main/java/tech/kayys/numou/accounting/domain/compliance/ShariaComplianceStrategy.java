package tech.kayys.syirkah.accounting.domain.compliance;

import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.islamic.ShariaContractType;
import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;

/**
 * Sharia AAOIFI & SAK Syariah compliance strategy (PSAK 101-112).
 * Strictly prohibits Riba (interest) accounts and requires contract tag validation.
 */
public final class ShariaComplianceStrategy implements AccountingComplianceStrategy {

    @Override
    public ComplianceStandard getStandard() {
        return ComplianceStandard.SHARIA_AAOIFI_SAK;
    }

    @Override
    public void validateAccount(Account account, ComplianceConfiguration config) {
        if (!config.allowRibaAccounts()) {
            String nameLower = account.getName().toLowerCase();
            String descLower = account.getDescription() != null ? account.getDescription().toLowerCase() : "";
            if (nameLower.contains("interest") || nameLower.contains("bunga") || nameLower.contains("riba") ||
                descLower.contains("interest") || descLower.contains("bunga")) {
                throw new IllegalArgumentException(
                        "Sharia compliance violation: Account '" + account.getName() + "' represents Riba/Interest which is prohibited");
            }
        }
    }

    @Override
    public void validateJournalEntry(JournalEntry entry, ComplianceConfiguration config) {
        if (config.enforceShariaContracts()) {
            if (entry.getShariaContractType() == null || entry.getShariaContractType() == ShariaContractType.NONE) {
                throw new IllegalStateException(
                        "Sharia compliance violation: Journal entry " + entry.getEntryNumber() + 
                        " must be classified with a valid Sharia contract type (e.g. Murabahah, Mudharabah, Ijarah)");
            }
        }
    }
}
