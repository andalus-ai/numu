package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.util.Objects;

/** A partner's capital contribution as agreed in the akad. */
public record CapitalContribution(PartnerId partner, CapitalKind kind, Money amount, String valuationNote)
        implements Serializable {
    public CapitalContribution {
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(amount, "amount");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("capital cannot be negative");
        }
    }

    public static CapitalContribution cash(PartnerId partner, Money amount) {
        return new CapitalContribution(partner, CapitalKind.CASH, amount, null);
    }
}
