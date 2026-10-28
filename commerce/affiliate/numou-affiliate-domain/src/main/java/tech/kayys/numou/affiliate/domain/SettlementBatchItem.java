package tech.kayys.numou.affiliate.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot of a payout selected for a settlement batch. */
public record SettlementBatchItem(UUID payoutId, UUID partnerId, BigDecimal amount, String currency) {
    public SettlementBatchItem {
        Objects.requireNonNull(payoutId, "payoutId is required");
        Objects.requireNonNull(partnerId, "partnerId is required");
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) throw new IllegalArgumentException("settlement item amount must be positive");
        Objects.requireNonNull(currency, "currency is required");
        currency = currency.trim().toUpperCase(Locale.ROOT);
        if (currency.isBlank()) throw new IllegalArgumentException("currency cannot be blank");
        Currency.getInstance(currency);
    }
}
