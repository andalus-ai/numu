package tech.kayys.numou.foundation.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PercentageTest {

    @Test
    void representsTenPercent() {
        var percentage = Percentage.of(10);

        assertEquals(new BigDecimal("10"), percentage.value());
        assertEquals(new BigDecimal("0.1"), percentage.factor());
    }

    @Test
    void rejectsMoreThanOneHundredPercent() {
        assertThrows(IllegalArgumentException.class, () -> Percentage.of(101));
    }

    @Test
    void rejectsNegativePercentage() {
        assertThrows(IllegalArgumentException.class, () -> Percentage.of(-1));
    }

}
