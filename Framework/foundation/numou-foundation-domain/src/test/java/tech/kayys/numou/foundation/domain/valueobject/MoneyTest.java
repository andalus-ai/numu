package tech.kayys.numou.foundation.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    private static final Currency IDR = Currency.IDR();
    private static final Currency USD = Currency.USD();

    @Test
    void addsSameCurrency() {
        var first = Money.of(new BigDecimal("10000"), IDR);
        var second = Money.of(new BigDecimal("5000"), IDR);

        var result = first.add(second);

        assertEquals(new BigDecimal("15000"), result.amount());
        assertEquals(IDR, result.currency());
    }

    @Test
    void subtractsSameCurrency() {
        var first = Money.of(new BigDecimal("10000"), IDR);
        var second = Money.of(new BigDecimal("3000"), IDR);

        var result = first.subtract(second);

        assertEquals(new BigDecimal("7000"), result.amount());
    }

    @Test
    void multipliesMoney() {
        var money = Money.of(new BigDecimal("10000"), IDR);

        var result = money.multiply(new BigDecimal("2.5"));

        assertEquals(new BigDecimal("25000.0"), result.amount());
    }

    @Test
    void rejectsCurrencyMismatch() {
        var idr = Money.of(new BigDecimal("10000"), IDR);
        var usd = Money.of(new BigDecimal("10"), USD);

        assertThrows(IllegalArgumentException.class, () -> idr.add(usd));
    }

    @Test
    void detectsPositiveMoney() {
        var money = Money.of(10000, IDR);

        assertTrue(money.isPositive());
        assertFalse(money.isZero());
        assertFalse(money.isNegative());
    }

    @Test
    void detectsNegativeMoney() {
        var money = Money.of(-10000, IDR);

        assertTrue(money.isNegative());
    }

    @Test
    void comparesMoney() {
        var first = Money.of(10000, IDR);
        var second = Money.of(5000, IDR);

        assertTrue(first.greaterThan(second));
        assertTrue(second.lessThan(first));
    }

}
