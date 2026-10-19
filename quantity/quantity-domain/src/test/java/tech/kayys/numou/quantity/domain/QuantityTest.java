package tech.kayys.numou.quantity.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuantityTest {
    @Test
    void normalizesUnitCodesAndAddsOnlyMatchingUnits() {
        Quantity metres = new Quantity(new BigDecimal("2.5"), new UnitOfMeasure(" M "));
        Quantity moreMetres = new Quantity(new BigDecimal("1.5"), new UnitOfMeasure("m"));

        assertEquals(new BigDecimal("4.0"), metres.add(moreMetres).value());
        assertEquals("m", metres.unit().code());
        assertThrows(IllegalArgumentException.class, () ->
                metres.add(new Quantity(BigDecimal.ONE, new UnitOfMeasure("kg"))));
    }

    @Test
    void rejectsNegativeValuesAndFactors() {
        assertThrows(IllegalArgumentException.class, () ->
                new Quantity(new BigDecimal("-0.1"), new UnitOfMeasure("m")));
        assertThrows(IllegalArgumentException.class, () ->
                new Quantity(BigDecimal.ONE, new UnitOfMeasure("m")).multiply(new BigDecimal("-2")));
    }

    @Test
    void createsNeutralScheduleLineWithoutCommercialFields() {
        UUID nodeId = UUID.randomUUID();
        QuantityScheduleLine line = QuantityScheduleLine.create(
                nodeId, "Q-01", "Concrete volume",
                new Quantity(new BigDecimal("3.25"), new UnitOfMeasure("m3")), 0);

        assertNotNull(line.id());
        assertEquals(nodeId, line.breakdownNodeId());
        assertEquals(new BigDecimal("3.25"), line.quantity().value());
        assertEquals("m3", line.quantity().unit().code());
    }

    @Test
    void rejectsInvalidSchedulePosition() {
        assertThrows(IllegalArgumentException.class, () -> new QuantityScheduleLine(
                UUID.randomUUID(), UUID.randomUUID(), "Q-01", "Concrete",
                new Quantity(BigDecimal.ONE, new UnitOfMeasure("m3")), -1));
    }
}
