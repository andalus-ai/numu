package tech.kayys.numou.planning.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScopeQuantityDefinitionTest {
    private final UUID tenant = UUID.randomUUID();
    private final UUID definitionId = UUID.randomUUID();

    @Test
    void definitionIsTenantOwnedVersionedAndNormalizesUnitCode() {
        var definition = new ScopeQuantityDefinition(definitionId, tenant, "WBS_NODE", "wbs-42",
                "DELIVERABLE", "Completed deliverable units", " unit ", new BigDecimal("12.500"), 1);
        assertEquals("UNIT", definition.unitCode());
        assertEquals(1, definition.revision());
        assertEquals(new BigDecimal("12.500"), definition.plannedQuantity());
    }

    @Test
    void rejectsNegativeOrOverPrecisionQuantitiesAndInvalidRevisions() {
        assertThrows(IllegalArgumentException.class, () -> definition("-1", 1));
        assertThrows(IllegalArgumentException.class, () -> definition("1.1234567", 1));
        assertThrows(IllegalArgumentException.class, () -> definition("1", 0));
    }

    @Test
    void measurementIsDatedEvidenceAndRejectsNegativeActualQuantity() {
        var measurement = new QuantityMeasurement(UUID.randomUUID(), tenant, definitionId,
                LocalDate.of(2026, 10, 20), new BigDecimal("4.25"),
                Instant.parse("2026-10-20T10:00:00Z"), "planner", "daily-42", "report-42");
        assertEquals(new BigDecimal("4.25"), measurement.actualQuantity());
        assertThrows(IllegalArgumentException.class, () -> new QuantityMeasurement(UUID.randomUUID(), tenant,
                definitionId, LocalDate.now(), new BigDecimal("-0.1"), Instant.now(),
                "planner", "bad-key", null));
    }

    private ScopeQuantityDefinition definition(String quantity, int revision) {
        return new ScopeQuantityDefinition(UUID.randomUUID(), tenant, "WBS_NODE", "wbs-42",
                "DELIVERABLE", "Units", "unit", new BigDecimal(quantity), revision);
    }
}
