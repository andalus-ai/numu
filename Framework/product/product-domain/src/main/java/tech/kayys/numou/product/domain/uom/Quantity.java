package tech.kayys.syirkah.product.domain.uom;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Measurable product domain quantity combining an amount and a unit of measure.
 */
public record Quantity(
        BigDecimal amount,
        UnitOfMeasure unit
) implements Comparable<Quantity> {

    public static final RoundingPolicy DEFAULT_ROUNDING = new RoundingPolicy(6, RoundingMode.HALF_UP);

    public Quantity {
        Objects.requireNonNull(amount, "amount cannot be null");
        Objects.requireNonNull(unit, "unit cannot be null");
    }

    public static Quantity of(BigDecimal amount, UnitOfMeasure unit) {
        return new Quantity(amount, unit);
    }

    public static Quantity of(long amount, UnitOfMeasure unit) {
        return new Quantity(BigDecimal.valueOf(amount), unit);
    }

    public static Quantity of(double amount, UnitOfMeasure unit) {
        return new Quantity(BigDecimal.valueOf(amount), unit);
    }

    public static Quantity zero(UnitOfMeasure unit) {
        return new Quantity(BigDecimal.ZERO, unit);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public Quantity add(Quantity other) {
        requireSameUnit(other);
        return new Quantity(amount.add(other.amount), unit);
    }

    public Quantity subtract(Quantity other) {
        requireSameUnit(other);
        return new Quantity(amount.subtract(other.amount), unit);
    }

    public Quantity multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor cannot be null");
        return new Quantity(amount.multiply(factor), unit);
    }

    public Quantity multiply(long factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    public Quantity divide(BigDecimal divisor, RoundingPolicy policy) {
        Objects.requireNonNull(divisor, "divisor cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");
        if (divisor.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return new Quantity(amount.divide(divisor, policy.scale(), policy.mode()), unit);
    }

    public Quantity round(RoundingPolicy policy) {
        Objects.requireNonNull(policy, "policy cannot be null");
        return new Quantity(amount.setScale(policy.scale(), policy.mode()), unit);
    }

    public Quantity convertTo(UnitOfMeasure target, UnitConversion conversion) {
        return convertTo(target, conversion, null);
    }

    public Quantity convertTo(UnitOfMeasure target, UnitConversion conversion, RoundingPolicy policy) {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(conversion, "conversion cannot be null");

        if (!unit.equals(conversion.from()) || !target.equals(conversion.to())) {
            throw new IllegalArgumentException(
                    "Conversion does not match source and target units: expected " 
                            + unit.code() + " -> " + target.code() 
                            + " but got " + conversion.from().code() + " -> " + conversion.to().code()
            );
        }

        BigDecimal convertedAmount = conversion.convert(amount);
        if (policy != null) {
            convertedAmount = convertedAmount.setScale(policy.scale(), policy.mode());
        }
        return new Quantity(convertedAmount, target);
    }

    private void requireSameUnit(Quantity other) {
        Objects.requireNonNull(other, "Quantity cannot be null");
        if (!unit.equals(other.unit)) {
            throw new IllegalArgumentException(
                    "Unit mismatch: " + unit.code() + " vs " + other.unit.code()
            );
        }
    }

    @Override
    public int compareTo(Quantity other) {
        requireSameUnit(other);
        return amount.compareTo(other.amount);
    }
}
