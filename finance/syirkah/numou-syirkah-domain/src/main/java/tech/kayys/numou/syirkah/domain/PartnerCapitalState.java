package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;

/**
 * Capital view of one partner. {@code basis} is capital contributed plus
 * retained
 * profit less returns; {@code balance} is basis less unrecovered losses.
 */
public record PartnerCapitalState(PartnerId partner, Money basis, Money balance) implements Serializable {
    public Money impairment() {
        Money m = basis.subtract(balance);
        return m.isNegative() ? Money.zero(basis.currency()) : m;
    }
}
