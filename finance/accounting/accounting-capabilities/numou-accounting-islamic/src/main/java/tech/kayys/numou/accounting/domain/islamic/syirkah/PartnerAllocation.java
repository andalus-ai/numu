package tech.kayys.numou.accounting.domain.islamic.syirkah;

import java.math.BigDecimal;
import java.util.Objects;

/** Immutable allocation in the contract currency. Amount is always non-negative. */
public record PartnerAllocation(String partyId, BigDecimal amount) {
    public PartnerAllocation {
        partyId = SyirkahPartyTerms.requireText(partyId, "partyId");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) throw new IllegalArgumentException("allocation amount must not be negative");
    }
}
