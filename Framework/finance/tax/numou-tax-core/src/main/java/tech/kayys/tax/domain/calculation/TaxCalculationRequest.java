package tech.kayys.tax.domain.calculation;

import tech.kayys.numou.foundation.domain.valueobject.MonetaryRoundingPolicy;

import java.util.List;
import java.util.Objects;

/**
 * Request for transactional tax calculation.
 */
public record TaxCalculationRequest(
        String transactionId,
        List<TaxCalculationLine> lines,
        boolean taxInclusive,
        MonetaryRoundingPolicy roundingPolicy
) {
    public TaxCalculationRequest {
        Objects.requireNonNull(transactionId, "transactionId cannot be null");
        lines = lines == null ? List.of() : List.copyOf(lines);
        roundingPolicy = roundingPolicy == null ? MonetaryRoundingPolicy.STANDARD_TWO_DECIMALS : roundingPolicy;
    }
}
