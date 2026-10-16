package tech.kayys.numou.foundation.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class QuantityTest {

    private static final Unit KG = Unit.of("kg");
    private static final Unit LITER = Unit.of("liter");

    @Test
    void addsSameUnit() {
        var first = Quantity.of(new BigDecimal("2.5"), KG);
        var second = Quantity.of(new BigDecimal("1.5"), KG);

        var result = first.add(second);

        assertEquals(new BigDecimal("4.0"), result.value());
    }

    @Test
    void rejectsDifferentUnits() {
        var kg = Quantity.of(new BigDecimal("2"), KG);
        var liter = Quantity.of(new BigDecimal("2"), LITER);

        assertThrows(IllegalArgumentException.class, () -> kg.add(liter));
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Quantity.of(new BigDecimal("-1"), KG)
        );
    }

}
