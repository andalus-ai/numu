package tech.kayys.numou.pos.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Currency-aware monetary amount for POS-owned snapshots and cash controls.
 *
 * <p>This type deliberately does not apply tax, pricing, discount, or currency
 * rounding policy. Those rules belong to their owning capabilities and must
 * be supplied explicitly by application services.</p>
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(currency, "currency cannot be null");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isNonNegative() {
        return amount.signum() >= 0;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other money cannot be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currency mismatch: " + currency + " != " + other.currency);
        }
    }
}
