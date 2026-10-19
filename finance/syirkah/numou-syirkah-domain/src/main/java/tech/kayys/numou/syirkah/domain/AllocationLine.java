package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;

/**
 * What one partner receives/bears from one close.
 *
 * @param capitalRestored  profit used first to repair previously impaired capital
 * @param profitShare      share of distributable profit (nisbah)
 * @param lossBorne        loss reducing the partner's capital (by capital proportion)
 * @param managerLiability loss the partner owes because of negligence/breach (mudharabah)
 * @param retained         part of profitShare retained as capital (equals profitShare on RETAIN)
 */
public record AllocationLine(PartnerId partner, Money capitalRestored, Money profitShare, Money lossBorne,
                             Money managerLiability, Money retained) implements Serializable {

    public Money payable() {
        return profitShare.subtract(retained);
    }

    public String canonical() {
        return partner.value() + ":" + capitalRestored.amount().toPlainString() + ","
                + profitShare.amount().toPlainString() + "," + lossBorne.amount().toPlainString() + ","
                + managerLiability.amount().toPlainString() + "," + retained.amount().toPlainString();
    }
}
