package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.util.Objects;

/**
 * Result of a period: net profit = revenue - direct costs - operating costs -
 * other costs.
 * Profit sharing is always computed on {@link #netResult()}, never on revenue.
 */
public record FinancialResult(Money revenue, Money directCosts, Money operatingCosts, Money otherCosts)
        implements Serializable {

    public FinancialResult {
        Objects.requireNonNull(revenue, "revenue");
        Objects.requireNonNull(directCosts, "directCosts");
        Objects.requireNonNull(operatingCosts, "operatingCosts");
        Objects.requireNonNull(otherCosts, "otherCosts");
        for (Money m : new Money[] { revenue, directCosts, operatingCosts, otherCosts }) {
            if (m.isNegative()) {
                throw new IllegalArgumentException("revenue and costs must be non-negative: " + m);
            }
        }
        var c = revenue.currency();
        if (!c.equals(directCosts.currency()) || !c.equals(operatingCosts.currency())
                || !c.equals(otherCosts.currency())) {
            throw new IllegalArgumentException("all amounts must share one currency");
        }
    }

    public static FinancialResult of(Money revenue, Money direct, Money operating, Money other) {
        return new FinancialResult(revenue, direct, operating, other);
    }

    /** Positive = profit, negative = loss. */
    public Money netResult() {
        return revenue.subtract(directCosts).subtract(operatingCosts).subtract(otherCosts);
    }

    public String canonical() {
        return revenue.amount().toPlainString() + "/" + directCosts.amount().toPlainString() + "/"
                + operatingCosts.amount().toPlainString() + "/" + otherCosts.amount().toPlainString();
    }
}
