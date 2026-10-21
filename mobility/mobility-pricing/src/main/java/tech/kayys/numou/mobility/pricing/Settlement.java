package tech.kayys.numou.mobility.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How the money of a completed ride moves. Definitions, with {@code F} = fare subtotal, {@code D} = promo discount
 * (funded by the platform), {@code B} = booking fee, {@code T} = tip, {@code c} = commission rate:
 * <pre>
 *   rider pays        R = F - D + B + T
 *   commission          = round(F * c)                  (on the fare only: not on tips or the booking fee)
 *   driver earns      E = F - commission + T            (a promo never reduces the driver's earning)
 *   platform keeps    R - E = commission + B - D        (negative when promo subsidy exceeds revenue)
 * </pre>
 * Ledger moves (a positive number credits the wallet):
 * <ul>
 *   <li>WALLET: rider {@code -R}, driver {@code +E}, platform {@code +(R-E)}</li>
 *   <li>CASH:   rider {@code 0} (paid the driver in cash), driver {@code E-R} (usually negative: owes the platform its share),
 *       platform {@code +(R-E)}</li>
 * </ul>
 * In both cases the three moves sum to exactly zero: no money is created or lost.
 */
public record Settlement(BigDecimal riderPays, BigDecimal commission, BigDecimal driverEarning, BigDecimal platformNet,
                         BigDecimal riderWalletMove, BigDecimal driverWalletMove, BigDecimal platformWalletMove) {

    public static Settlement compute(BigDecimal fare, BigDecimal discount, BigDecimal bookingFee, BigDecimal tip,
                                     BigDecimal commissionRate, PaymentMethod method, int fractionDigits) {
        if (commissionRate.signum() < 0 || commissionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("commission rate must be between 0 and 1");
        }
        if (fare.signum() < 0 || discount.signum() < 0 || bookingFee.signum() < 0 || tip.signum() < 0 || discount.compareTo(fare) > 0) {
            throw new IllegalArgumentException("invalid fare components");
        }
        final BigDecimal commission = fare.multiply(commissionRate).setScale(fractionDigits, RoundingMode.HALF_UP);
        final BigDecimal rider = fare.subtract(discount).add(bookingFee).add(tip);
        final BigDecimal driver = fare.subtract(commission).add(tip);
        final BigDecimal platform = rider.subtract(driver);
        final BigDecimal zero = BigDecimal.ZERO.setScale(fractionDigits);
        return method == PaymentMethod.WALLET
                ? new Settlement(rider, commission, driver, platform, rider.negate(), driver, platform)
                : new Settlement(rider, commission, driver, platform, zero, driver.subtract(rider), platform);
    }

    /** A cancellation/no-show fee: charged to the rider's wallet, shared between driver and platform. */
    public static Settlement forFee(BigDecimal fee, CancellationPolicy.Split split, int fractionDigits) {
        final BigDecimal zero = BigDecimal.ZERO.setScale(fractionDigits);
        return new Settlement(fee, zero, split.driverPart(), split.platformPart(), fee.negate(), split.driverPart(), split.platformPart());
    }

    /** Sum of every wallet move; always zero. */
    public BigDecimal net() {
        return riderWalletMove.add(driverWalletMove).add(platformWalletMove);
    }
}
