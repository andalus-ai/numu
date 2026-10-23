package tech.kayys.numou.planning.adapter.postgres;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.domain.QuantityMeasurement;
import tech.kayys.numou.planning.domain.QuantityMeasurementCorrection;
import tech.kayys.numou.planning.domain.ScopeQuantityDefinition;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JdbcQuantityCorrectionRepositoryIntegrationTest {
    @Inject JdbcScopeQuantityRepository quantityRepository;
    @Inject JdbcQuantityCorrectionRepository correctionRepository;
    @Inject javax.sql.DataSource dataSource;

    @Test
    void correctionIsAuditedWithoutMutatingOriginalMeasurements() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        var definition = new ScopeQuantityDefinition(definitionId, tenant, "DELIVERABLE", "deliverable-1",
                "COUNT", "Delivered units", "unit", new BigDecimal("10"), 1);
        assertTrue(quantityRepository.createDefinition(definition));

        var original = measurement(tenant, definitionId, "original-1", "3");
        var replacement = measurement(tenant, definitionId, "replacement-1", "4");
        assertTrue(quantityRepository.appendMeasurement(original));
        assertTrue(quantityRepository.appendMeasurement(replacement));

        var correction = new QuantityMeasurementCorrection(UUID.randomUUID(), tenant, definitionId,
                original.id(), replacement.id(), Instant.parse("2026-10-23T10:00:00Z"),
                "auditor", "Verified source report corrected the original quantity", "correction-1");
        assertTrue(correctionRepository.appendCorrection(correction));
        assertFalse(correctionRepository.appendCorrection(correction));
        assertEquals(1, correctionRepository.findCorrections(tenant, definitionId, original.id()).size());
        assertEquals(new BigDecimal("3.000000"), quantityRepository.findMeasurementHistory(tenant, definitionId)
                .stream().filter(m -> m.id().equals(original.id())).findFirst().orElseThrow().actualQuantity());
        assertEquals(new BigDecimal("4.000000"), quantityRepository.findMeasurementHistory(tenant, definitionId)
                .stream().filter(m -> m.id().equals(replacement.id())).findFirst().orElseThrow().actualQuantity());

        assertThrows(Exception.class, () -> deleteCorrection(tenant, correction.id()));
        assertThrows(RuntimeException.class, () -> correctionRepository.appendCorrection(
                new QuantityMeasurementCorrection(UUID.randomUUID(), tenant, definitionId,
                        original.id(), UUID.randomUUID(), Instant.now(), "auditor", "Missing replacement",
                        "invalid-fk")));
    }

    private QuantityMeasurement measurement(UUID tenant, UUID definition, String key, String amount) {
        return new QuantityMeasurement(UUID.randomUUID(), tenant, definition, LocalDate.of(2026, 10, 22),
                new BigDecimal(amount), Instant.parse("2026-10-22T10:00:00Z"), "planner", key, "daily-report");
    }

    private void deleteCorrection(UUID tenant, UUID id) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement s = c.prepareStatement(
                     "DELETE FROM planning_quantity_measurement_correction WHERE tenant_id = ? AND correction_id = ?")) {
            s.setObject(1, tenant);
            s.setObject(2, id);
            s.executeUpdate();
        }
    }
}
