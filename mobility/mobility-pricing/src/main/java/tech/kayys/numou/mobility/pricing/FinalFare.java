package tech.kayys.numou.mobility.pricing;

import java.math.BigDecimal;

/** What the rider is actually charged for a finished trip. */
public record FinalFare(Basis basis, BigDecimal subtotal, BigDecimal discount, BigDecimal bookingFee, BigDecimal total) {

    public enum Basis {
        /** Within tolerance of the estimate: the price shown at booking stands. */
        UPFRONT,
        /** The trip differed materially from the estimate: recalculated from actual distance/time. */
        METERED
    }
}
