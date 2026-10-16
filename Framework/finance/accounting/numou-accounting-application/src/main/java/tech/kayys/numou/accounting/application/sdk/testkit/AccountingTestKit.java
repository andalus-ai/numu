package tech.kayys.syirkah.accounting.application.sdk.testkit;

import tech.kayys.syirkah.accounting.application.sdk.bootstrap.AccountingBootstrap;
import tech.kayys.syirkah.accounting.application.sdk.bootstrap.AccountingEngine;
import tech.kayys.syirkah.accounting.application.sdk.plugin.PlatformPlugin;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Test helper that gives third-party module authors a simple, fluent API for
 * writing accounting tests without framework boilsyirkahlate.
 */
public final class AccountingTestKit {

    private final AccountingEngine engine;

    private AccountingTestKit(AccountingEngine engine) {
        this.engine = engine;
    }

    public static Builder create() { return new Builder(); }

    public static final class Builder {
        private final List<PlatformPlugin> plugins = new ArrayList<>();

        public Builder withPlugin(PlatformPlugin plugin) {
            plugins.add(plugin);
            return this;
        }

        public AccountingTestKit build() {
            AccountingBootstrap bootstrap = AccountingBootstrap.lite();
            plugins.forEach(bootstrap::withPlugin);
            return new AccountingTestKit(bootstrap.build());
        }
    }

    public AccountingEngine engine() { return engine; }

    /**
     * Asserts that a journal entry is balanced (sum of debits == sum of credits).
     */
    public void assertJournalBalanced(JournalEntry entry) {
        Money totalDebit = null;
        Money totalCredit = null;

        for (JournalEntry.JournalLine line : entry.getLines()) {
            if (totalDebit == null) {
                totalDebit = line.debit();
                totalCredit = line.credit();
            } else {
                totalDebit = totalDebit.add(line.debit());
                totalCredit = totalCredit.add(line.credit());
            }
        }

        if (totalDebit != null && totalCredit != null && !totalDebit.amount().equals(totalCredit.amount())) {
            throw new AssertionError(
                    String.format("Journal entry %s is NOT balanced: debits=%s credits=%s",
                            entry.getId(), totalDebit, totalCredit));
        }
    }

    /**
     * Asserts that the total debit amount on an entry equals the expected value.
     */
    public void assertTotalDebit(JournalEntry entry, BigDecimal expected) {
        BigDecimal actual = entry.getLines().stream()
                .map(l -> l.debit().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (actual.compareTo(expected) != 0) {
            throw new AssertionError(
                    String.format("Expected total debit %s but was %s",
                            expected.toPlainString(), actual.toPlainString()));
        }
    }

    /**
     * Asserts that the number of active plugins equals {@code expected}.
     */
    public void assertPluginCount(int expected) {
        int actual = engine.pluginRegistry().all().size();
        if (actual != expected) {
            throw new AssertionError(
                    String.format("Expected %d plugin(s) but found %d", expected, actual));
        }
    }
}
