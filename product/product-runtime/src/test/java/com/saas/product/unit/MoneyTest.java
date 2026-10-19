package com.saas.product.unit;

import com.saas.product.core.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Money value object.
 * Verifies immutability, arithmetic, and currency safety.
 */
class MoneyTest {

    @Test
    @DisplayName("Money.of creates correct amount and currency")
    void construction() {
        Money m = Money.of(new BigDecimal("150000.50"), "IDR");
        assertThat(m.getAmount()).isEqualByComparingTo("150001"); // IDR has 0 fraction digits
        assertThat(m.getCurrencyCode()).isEqualTo("IDR");
    }

    @Test
    @DisplayName("Addition returns new instance, operands unchanged")
    void additionIsImmutable() {
        Money a = Money.of(new BigDecimal("100000"), "IDR");
        Money b = Money.of(new BigDecimal("50000"),  "IDR");
        Money c = a.add(b);

        assertThat(c.getAmount()).isEqualByComparingTo("150000");
        assertThat(a.getAmount()).isEqualByComparingTo("100000"); // unchanged
    }

    @Test
    @DisplayName("Subtraction works when result is positive")
    void subtraction() {
        Money a = Money.of(new BigDecimal("100000"), "IDR");
        Money b = Money.of(new BigDecimal("30000"),  "IDR");
        Money c = a.subtract(b);
        assertThat(c.getAmount()).isEqualByComparingTo("70000");
    }

    @Test
    @DisplayName("Subtraction to negative throws")
    void subtractionNegativeThrows() {
        Money a = Money.of(new BigDecimal("10000"), "IDR");
        Money b = Money.of(new BigDecimal("50000"), "IDR");
        assertThatIllegalArgumentException().isThrownBy(() -> a.subtract(b));
    }

    @Test
    @DisplayName("Multiply by int")
    void multiplyByInt() {
        Money a = Money.of(new BigDecimal("25000"), "IDR");
        Money result = a.multiply(4);
        assertThat(result.getAmount()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("percentage() computes correct fraction")
    void percentage() {
        Money base = Money.of(new BigDecimal("100000"), "IDR");
        Money ten  = base.percentage(new BigDecimal("10"));
        assertThat(ten.getAmount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Cross-currency addition throws")
    void crossCurrencyThrows() {
        Money idr = Money.of(new BigDecimal("100000"), "IDR");
        Money usd = Money.of(new BigDecimal("10"),     "USD");
        assertThatIllegalArgumentException().isThrownBy(() -> idr.add(usd));
    }

    @Test
    @DisplayName("Money.zero returns zero for given currency")
    void zero() {
        Money z = Money.zero("IDR");
        assertThat(z.isZero()).isTrue();
    }

    @Test
    @DisplayName("isGreaterThan works correctly")
    void isGreaterThan() {
        Money big   = Money.of(new BigDecimal("500"), "USD");
        Money small = Money.of(new BigDecimal("100"), "USD");
        assertThat(big.isGreaterThan(small)).isTrue();
        assertThat(small.isGreaterThan(big)).isFalse();
    }

    @Test
    @DisplayName("Negative amount at construction throws")
    void negativeAmountThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of(new BigDecimal("-1000"), "IDR"));
    }

    @Test
    @DisplayName("Equality is based on amount and currency")
    void equality() {
        Money a = Money.of(new BigDecimal("100000"), "IDR");
        Money b = Money.of(new BigDecimal("100000"), "IDR");
        Money c = Money.of(new BigDecimal("100001"), "IDR");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("toString includes currency code and amount")
    void toStringTest() {
        Money m = Money.of(new BigDecimal("75000"), "IDR");
        assertThat(m.toString()).contains("IDR").contains("75000");
    }
}
