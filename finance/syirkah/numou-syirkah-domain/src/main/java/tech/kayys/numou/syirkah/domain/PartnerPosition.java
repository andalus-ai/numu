package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;

/**
 * Derived position of a partner (never stored as truth; folded from movements).
 *
 * @param basis     capital contributed + retained profit - returns
 * @param balance   basis less unrecovered losses
 * @param payable   allocated profit not yet distributed or retained
 * @param liability amounts the partner owes the syirkah (manager fault,
 *                  deficit)
 */
public record PartnerPosition(PartnerId partner, Money basis, Money balance, Money payable, Money liability)
        implements Serializable {

    public PartnerCapitalState capitalState() {
        return new PartnerCapitalState(partner, basis, balance);
    }
}
