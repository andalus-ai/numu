package tech.kayys.syirkah.accounting.application.service;

import tech.kayys.syirkah.accounting.domain.compliance.*;
import tech.kayys.syirkah.accounting.domain.valueobject.ComplianceStandard;
import tech.kayys.syirkah.accounting.domain.model.Account;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Central engine for orchestrating multi-standard accounting compliance (IFRS, GAAP, SAK, Sharia, Hybrid).
 */
public class AccountingComplianceEngine {

    private final Map<ComplianceStandard, AccountingComplianceStrategy> strategies = new EnumMap<>(ComplianceStandard.class);
    private volatile ComplianceConfiguration configuration;

    public AccountingComplianceEngine() {
        this(ComplianceConfiguration.sakIndonesiaDefault());
    }

    public AccountingComplianceEngine(ComplianceConfiguration initialConfig) {
        this.configuration = Objects.requireNonNull(initialConfig);
        registerDefaultStrategies();
    }

    private void registerDefaultStrategies() {
        registerStrategy(new IfrsComplianceStrategy());
        registerStrategy(new UsGaapComplianceStrategy());
        registerStrategy(new SakIndonesiaComplianceStrategy());
        registerStrategy(new ShariaComplianceStrategy());
        registerStrategy(new HybridComplianceStrategy());
    }

    public void registerStrategy(AccountingComplianceStrategy strategy) {
        strategies.put(strategy.getStandard(), strategy);
    }

    public ComplianceConfiguration getConfiguration() {
        return configuration;
    }

    public void updateConfiguration(ComplianceConfiguration newConfig) {
        this.configuration = Objects.requireNonNull(newConfig);
    }

    public AccountingComplianceStrategy getActiveStrategy() {
        AccountingComplianceStrategy strategy = strategies.get(configuration.standard());
        if (strategy == null) {
            throw new IllegalStateException("No strategy registered for compliance standard: " + configuration.standard());
        }
        return strategy;
    }

    public void validateAccount(Account account) {
        getActiveStrategy().validateAccount(account, configuration);
    }

    public void validateJournalEntry(JournalEntry entry) {
        getActiveStrategy().validateJournalEntry(entry, configuration);
    }
}
