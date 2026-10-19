package tech.kayys.numou.accounting.application.compliance;

import tech.kayys.numou.accounting.domain.compliance.ComplianceConfiguration;
import tech.kayys.numou.accounting.domain.model.JournalEntry;

public interface ComplianceRule {
    String name();
    void evaluate(JournalEntry entry, ComplianceConfiguration config);
}
