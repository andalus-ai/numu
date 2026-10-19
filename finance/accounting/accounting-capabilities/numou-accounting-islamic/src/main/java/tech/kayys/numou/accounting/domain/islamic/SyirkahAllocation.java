package tech.kayys.numou.accounting.domain.islamic;

import java.math.BigDecimal;
import java.util.Objects;

/** Calculated allocation; this is not itself a journal entry or cash payment. */
public record SyirkahAllocation(String partyId, BigDecimal amount, String currencyCode, String basis) {
    public SyirkahAllocation {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyCode, "currencyCode");
        Objects.requireNonNull(basis, "basis");
        if (partyId.isBlank() || currencyCode.isBlank() || basis.isBlank()) {
            throw new IllegalArgumentException("allocation identifiers and basis must not be blank");
        }
        if (amount.signum() < 0) throw new IllegalArgumentException("allocation amount must not be negative");
    }
}
