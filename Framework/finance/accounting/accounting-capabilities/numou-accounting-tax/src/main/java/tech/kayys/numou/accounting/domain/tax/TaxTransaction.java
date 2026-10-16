package tech.kayys.syirkah.accounting.domain.tax;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record of tax computed on a commercial event.
 */
public record TaxTransaction(
        TaxTransactionId id,
        TaxKind kind,
        TaxDirection direction,
        String jurisdiction,
        BigDecimal baseAmount,
        BigDecimal taxRatePercentage,
        BigDecimal taxAmount,
        String currency,
        String documentReference,
        Instant occurredAt
) {
    public TaxTransaction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(direction);
        Objects.requireNonNull(jurisdiction);
        Objects.requireNonNull(baseAmount);
        Objects.requireNonNull(taxRatePercentage);
        Objects.requireNonNull(taxAmount);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(documentReference);
        Objects.requireNonNull(occurredAt);
    }
}
