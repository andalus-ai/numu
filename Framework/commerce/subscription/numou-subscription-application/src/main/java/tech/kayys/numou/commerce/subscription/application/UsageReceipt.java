package tech.kayys.syirkah.commerce.subscription.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of recording one usage entry: the new running total and
 * the remaining quota (empty = unlimited).
 */
public record UsageReceipt(
        String code,
        BigDecimal consumed,
        Optional<BigDecimal> remaining
) {

    public UsageReceipt {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(consumed, "consumed cannot be null");
        Objects.requireNonNull(remaining, "remaining cannot be null");
    }
}
