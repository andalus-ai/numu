package tech.kayys.syirkah.restaurant.reservation.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

/**
 * What cancelling (or not showing up) costs the guest and what is returned.
 *
 * @param charge       why money is kept
 * @param chargeAmount amount kept (zero when {@link Charge#NONE})
 * @param refundAmount deposit to give back (zero when none was paid or it is forfeited)
 */
public record CancellationOutcome(Charge charge, Money chargeAmount, Money refundAmount) {

    public enum Charge { NONE, LATE_FEE, DEPOSIT_FORFEITED }
}
