package tech.kayys.numou.construction.domain.boq;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.quantity.domain.Quantity;
import tech.kayys.numou.quantity.domain.UnitOfMeasure;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoqQuantityTest {
    @Test
    void adaptsToAndFromReusableQuantityWithoutChangingValueOrNormalizedUnit() {
        BoqQuantity boqQuantity = new BoqQuantity(new BigDecimal("12.50"), " M3 ");

        Quantity quantity = boqQuantity.toQuantity();
        assertEquals(new BigDecimal("12.50"), quantity.value());
        assertEquals(new UnitOfMeasure("m3"), quantity.unit());

        assertEquals(new BoqQuantity(new BigDecimal("12.50"), "m3"), BoqQuantity.from(quantity));
    }
}
