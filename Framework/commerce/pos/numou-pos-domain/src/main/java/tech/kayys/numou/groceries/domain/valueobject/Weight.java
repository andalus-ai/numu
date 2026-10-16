package tech.kayys.syirkah.groceries.domain.valueobject;

import tech.kayys.syirkah.foundation.domain.valueobject.ValueObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Weight value object with unit support.
 */
public final class Weight implements ValueObject {

    private static final long serialVersionUID = 1L;

    private final BigDecimal value;
    private final WeightUnit unit;

    public Weight(BigDecimal value, WeightUnit unit) {
        this.value = value.setScale(3, RoundingMode.HALF_UP);
        this.unit = unit;
        validate();
    }

    @Override
    public void validate() {
        if (value == null) {
            throw new IllegalArgumentException("Weight value cannot be null");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Weight cannot be negative");
        }
        if (unit == null) {
            throw new IllegalArgumentException("Weight unit cannot be null");
        }
    }

    public BigDecimal getValue() { return value; }
    public WeightUnit getUnit() { return unit; }

    public Weight add(Weight other) {
        if (this.unit != other.unit) {
            BigDecimal thisGrams = this.toGrams();
            BigDecimal otherGrams = other.toGrams();
            return Weight.fromGrams(thisGrams.add(otherGrams));
        }
        return new Weight(value.add(other.value), unit);
    }

    public Weight subtract(Weight other) {
        if (this.unit != other.unit) {
            BigDecimal thisGrams = this.toGrams();
            BigDecimal otherGrams = other.toGrams();
            return Weight.fromGrams(thisGrams.subtract(otherGrams));
        }
        return new Weight(value.subtract(other.value), unit);
    }

    public Weight multiply(BigDecimal multiplier) {
        return new Weight(value.multiply(multiplier), unit);
    }

    public Weight multiply(int multiplier) {
        return multiply(BigDecimal.valueOf(multiplier));
    }

    public BigDecimal toGrams() {
        return switch (unit) {
            case GRAM -> value;
            case KILOGRAM -> value.multiply(BigDecimal.valueOf(1000));
            case OUNCE -> value.multiply(BigDecimal.valueOf(28.3495));
            case POUND -> value.multiply(BigDecimal.valueOf(453.592));
        };
    }

    public BigDecimal toKilograms() {
        return toGrams().divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    }

    public int compareTo(Weight other) {
        return this.toGrams().compareTo(other.toGrams());
    }

    public boolean isGreaterThan(Weight other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Weight other) {
        return compareTo(other) < 0;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Weight weight = (Weight) o;
        return value.compareTo(weight.value) == 0 && unit == weight.unit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, unit);
    }

    @Override
    public String toString() {
        return value.toPlainString() + " " + unit.getSymbol();
    }

    public static Weight of(BigDecimal value, WeightUnit unit) {
        return new Weight(value, unit);
    }

    public static Weight of(String value, WeightUnit unit) {
        return new Weight(new BigDecimal(value), unit);
    }

    public static Weight of(double value, WeightUnit unit) {
        return new Weight(BigDecimal.valueOf(value), unit);
    }

    public static Weight fromGrams(BigDecimal grams) {
        return new Weight(grams, WeightUnit.GRAM);
    }

    public static Weight fromGrams(double grams) {
        return fromGrams(BigDecimal.valueOf(grams));
    }

    public static Weight fromKilograms(BigDecimal kg) {
        return new Weight(kg, WeightUnit.KILOGRAM);
    }

    public static Weight zero() {
        return new Weight(BigDecimal.ZERO, WeightUnit.GRAM);
    }

    public enum WeightUnit {
        GRAM("g"),
        KILOGRAM("kg"),
        OUNCE("oz"),
        POUND("lb");

        private final String symbol;

        WeightUnit(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }
}
