package tech.kayys.syirkah.accounting.application.compliance;

import tech.kayys.syirkah.accounting.domain.compliance.ComplianceConfiguration;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;

public interface ComplianceRule {
    String name();
    void evaluate(JournalEntry entry, ComplianceConfiguration config);
}
