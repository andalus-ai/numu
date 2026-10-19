package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;

/**
 * Independent cross-check of the partner ledger.
 *
 * <p>Identity: {@code sum(capital balance + profit payable - liability)} over all partners must equal
 * {@code net cash contributed (in - out) + sum of net results of effective closes (and of the liquidation)}.
 * The left side is folded from movements; the right side comes from cash flows and the immutable close
 * snapshots, so a lost, duplicated or forged movement shows up as a non-zero {@link #difference()}.</p>
 */
public record EquityReconciliation(Money partnerEquity, Money netCashContributed, Money recognizedResult,
                                   Money difference) implements Serializable {

    public boolean balanced() {
        return difference.isZero();
    }
}
