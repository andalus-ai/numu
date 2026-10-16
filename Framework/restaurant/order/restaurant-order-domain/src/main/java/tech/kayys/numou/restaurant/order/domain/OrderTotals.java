package tech.kayys.syirkah.restaurant.order.domain;

import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** The bill. Every component is rounded to the currency's minor unit, and {@code total} is the sum of the rounded parts. */
public record OrderTotals(Money subtotal, Money discount, Money serviceCharge, Money tax, Money total) {

    /**
     * Divides the total into {@code ways} shares that add up to <em>exactly</em> the total: some shares are one minor
     * unit larger (the first {@code remainder} of them) instead of everyone being rounded and the bill ending up
     * a few rupiah short or over.
     */
    public List<Money> splitEvenly(int ways, int fractionDigits) {
        if (ways < 1 || ways > 50) {
            throw new IllegalArgumentException("Split between 1 and 50 payers");
        }
        final BigDecimal unit = BigDecimal.ONE.movePointLeft(fractionDigits);
        final long minorTotal = total.amount().movePointRight(fractionDigits).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        final long base = minorTotal / ways;
        final long remainder = minorTotal % ways;
        final var shares = new ArrayList<Money>();
        for (int i = 0; i < ways; i++) {
            final long minor = base + (i < remainder ? 1 : 0);
            shares.add(Money.of(BigDecimal.valueOf(minor).multiply(unit).setScale(fractionDigits, RoundingMode.UNNECESSARY), total.currency()));
        }
        return shares;
    }
}
