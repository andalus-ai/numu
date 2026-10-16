package tech.kayys.syirkah.accounting.application.currency;

import tech.kayys.syirkah.accounting.domain.currency.ExchangeRateProvider;
import tech.kayys.syirkah.accounting.domain.identifier.AccountId;
import tech.kayys.syirkah.accounting.domain.identifier.JournalEntryId;
import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.model.JournalEntry;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Period-end multi-currency FX revaluation engine.
 * Computes unrealized foreign exchange gain/loss on monetary foreign balances
 * and generates adjusting double-entry journal entries.
 */
public class FxRevaluationEngine {

    private final ExchangeRateProvider exchangeRateProvider;

    public FxRevaluationEngine(ExchangeRateProvider exchangeRateProvider) {
        this.exchangeRateProvider = Objects.requireNonNull(exchangeRateProvider);
    }

    public record RevaluationResult(
            Currency foreignCurrency,
            BigDecimal foreignBalance,
            BigDecimal originalRate,
            BigDecimal closingRate,
            BigDecimal gainLossAmount, // positive = gain, negative = loss
            JournalEntry adjustingJournalEntry
    ) {}

    public RevaluationResult revalueAccount(
            TenantRef tenantId,
            LedgerId ledgerId,
            AccountId monetaryAccountId,
            AccountId fxGainAccountId,
            AccountId fxLossAccountId,
            Currency foreignCurrency,
            Currency baseCurrency,
            BigDecimal foreignBalance,
            BigDecimal bookedBaseAmount,
            LocalDate asOfDate,
            String createdBy) {

        BigDecimal closingRate = exchangeRateProvider.getRate(foreignCurrency, baseCurrency, asOfDate)
                .orElseThrow(() -> new IllegalArgumentException("No exchange rate found for " + foreignCurrency + " to " + baseCurrency));
        BigDecimal revaluedBaseAmount = foreignBalance.multiply(closingRate);
        BigDecimal gainLoss = revaluedBaseAmount.subtract(bookedBaseAmount);

        JournalEntryId journalId = JournalEntryId.generate();
        Instant entryDate = asOfDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        JournalEntry journal = new JournalEntry(
                journalId, tenantId, ledgerId,
                "FX-REV-" + monetaryAccountId.getValue() + "-" + asOfDate,
                entryDate,
                "FX Revaluation for account " + monetaryAccountId.getValue());

        if (gainLoss.compareTo(BigDecimal.ZERO) > 0) {
            // Gain: Dr. Monetary Asset, Cr. Unrealized FX Gain
            Money amount = Money.of(gainLoss, baseCurrency);
            journal.addLine(monetaryAccountId, amount, Money.zero(baseCurrency.code()), "FX Revaluation Gain - " + foreignCurrency.code());
            journal.addLine(fxGainAccountId, Money.zero(baseCurrency.code()), amount, "Unrealized FX Gain");
        } else if (gainLoss.compareTo(BigDecimal.ZERO) < 0) {
            // Loss: Dr. Unrealized FX Loss, Cr. Monetary Asset
            Money amount = Money.of(gainLoss.abs(), baseCurrency);
            journal.addLine(fxLossAccountId, amount, Money.zero(baseCurrency.code()), "Unrealized FX Loss");
            journal.addLine(monetaryAccountId, Money.zero(baseCurrency.code()), amount, "FX Revaluation Loss - " + foreignCurrency.code());
        }

        BigDecimal originalRate = foreignBalance.compareTo(BigDecimal.ZERO) == 0 ? 
                BigDecimal.ZERO : bookedBaseAmount.divide(foreignBalance, 4, java.math.RoundingMode.HALF_UP);

        return new RevaluationResult(foreignCurrency, foreignBalance, originalRate, closingRate, gainLoss, journal);
    }
}
