package tech.kayys.syirkah.accounting.application.compliance;

import tech.kayys.syirkah.accounting.domain.compliance.ComplianceConfiguration;
import tech.kayys.syirkah.accounting.domain.islamic.ShariaContractType;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;

import java.util.ArrayList;
import java.util.List;

public class ComplianceRulePipeline {

    private final List<ComplianceRule> rules = new ArrayList<>();

    public ComplianceRulePipeline() {
        registerDefaultRules();
    }

    private void registerDefaultRules() {
        // Rule 1: Double entry non-empty
        rules.add(new ComplianceRule() {
            @Override
            public String name() { return "NonEmptyJournalRule"; }
            @Override
            public void evaluate(JournalEntry entry, ComplianceConfiguration config) {
                if (entry.getLines().isEmpty()) {
                    throw new IllegalStateException("Journal entry must have at least one line");
                }
            }
        });

        // Rule 2: Sharia contract tagging
        rules.add(new ComplianceRule() {
            @Override
            public String name() { return "ShariaContractTagRule"; }
            @Override
            public void evaluate(JournalEntry entry, ComplianceConfiguration config) {
                if (config.enforceShariaContracts()) {
                    if (entry.getShariaContractType() == null || entry.getShariaContractType() == ShariaContractType.NONE) {
                        throw new IllegalStateException(
                                "Sharia compliance violation: Transaction must specify a valid contract type (Murabahah, Mudharabah, etc.)");
                    }
                }
            }
        });
    }

    public void addRule(ComplianceRule rule) {
        rules.add(rule);
    }

    public void execute(JournalEntry entry, ComplianceConfiguration config) {
        for (ComplianceRule rule : rules) {
            rule.evaluate(entry, config);
        }
    }
}
