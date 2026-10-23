package tech.kayys.numou.planning.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.QuantityCorrectionRepository;
import tech.kayys.numou.planning.application.port.ScopeQuantityRepository;
import tech.kayys.numou.planning.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RecordQuantityMeasurementCorrectionTest {
    @Test
    void correctionMustReferenceTwoMeasurementsInSameTenantAndDefinition() {
        UUID tenant = UUID.randomUUID();
        UUID definition = UUID.randomUUID();
        var original = measurement(tenant, definition, "3", "original");
        var replacement = measurement(tenant, definition, "4", "replacement");
        var quantities = new FakeQuantities(List.of(original, replacement));
        var corrections = new FakeCorrections();
        var useCase = new RecordQuantityMeasurementCorrection(quantities, corrections);
        var correction = new QuantityMeasurementCorrection(UUID.randomUUID(), tenant, definition,
                original.id(), replacement.id(), Instant.parse("2026-10-23T10:00:00Z"),
                "reviewer", "Corrected quantity after verification", "correction-1");

        assertTrue(useCase.execute(correction));
        assertFalse(useCase.execute(correction));
        assertEquals(1, corrections.records.size());
    }

    @Test
    void rejectsMissingReplacementEvidence() {
        UUID tenant = UUID.randomUUID();
        UUID definition = UUID.randomUUID();
        var original = measurement(tenant, definition, "3", "original");
        var quantities = new FakeQuantities(List.of(original));
        var correction = new QuantityMeasurementCorrection(UUID.randomUUID(), tenant, definition,
                original.id(), UUID.randomUUID(), Instant.now(), "reviewer", "Correction", "missing");
        assertThrows(IllegalArgumentException.class,
                () -> new RecordQuantityMeasurementCorrection(quantities, new FakeCorrections()).execute(correction));
    }

    private QuantityMeasurement measurement(UUID tenant, UUID definition, String amount, String key) {
        return new QuantityMeasurement(UUID.randomUUID(), tenant, definition, LocalDate.of(2026, 10, 22),
                new BigDecimal(amount), Instant.parse("2026-10-22T10:00:00Z"), "planner", key, null);
    }

    private static final class FakeQuantities implements ScopeQuantityRepository {
        private final List<QuantityMeasurement> history;
        FakeQuantities(List<QuantityMeasurement> history) { this.history = history; }
        @Override public boolean createDefinition(ScopeQuantityDefinition definition) { return true; }
        @Override public Optional<ScopeQuantityDefinition> findDefinition(UUID tenant, UUID id) { return Optional.empty(); }
        @Override public List<ScopeQuantityDefinition> findDefinitionsForScope(UUID tenant, String type, String id) { return List.of(); }
        @Override public boolean appendMeasurement(QuantityMeasurement measurement) { return true; }
        @Override public List<QuantityMeasurement> findMeasurementHistory(UUID tenant, UUID definitionId) {
            return history.stream().filter(m -> m.tenantId().equals(tenant) && m.definitionId().equals(definitionId)).toList();
        }
        @Override public Optional<QuantityMeasurement> findLatestMeasurementAsOf(UUID tenant, UUID definitionId, LocalDate asOf) {
            return Optional.empty();
        }
    }

    private static final class FakeCorrections implements QuantityCorrectionRepository {
        private final List<QuantityMeasurementCorrection> records = new ArrayList<>();
        @Override public boolean appendCorrection(QuantityMeasurementCorrection correction) {
            if (records.stream().anyMatch(c -> c.tenantId().equals(correction.tenantId())
                    && c.idempotencyKey().equals(correction.idempotencyKey()))) return false;
            records.add(correction);
            return true;
        }
        @Override public List<QuantityMeasurementCorrection> findCorrections(UUID tenant, UUID definition, UUID original) {
            return records.stream().filter(c -> c.tenantId().equals(tenant) && c.definitionId().equals(definition)
                    && c.originalMeasurementId().equals(original)).toList();
        }
    }
}
