package tech.kayys.numou.planning.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.*;
import tech.kayys.numou.planning.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GetAcceptedScopeQuantityAsOfTest {
    @Test void laterApprovalDoesNotRewriteAnEarlierAsOfSnapshot() {
        UUID tenant = UUID.randomUUID(), definition = UUID.randomUUID();
        var original = measurement(tenant, definition, "3", "2026-10-01", "original");
        var replacement = measurement(tenant, definition, "4", "2026-10-02", "replacement");
        var correction = new QuantityMeasurementCorrection(UUID.randomUUID(), tenant, definition, original.id(),
                replacement.id(), Instant.parse("2026-10-02T10:00:00Z"), "auditor", "Verified correction", "corr-1");
        var approval = new QuantityCorrectionDecision(UUID.randomUUID(), tenant, definition, correction.id(),
                QuantityCorrectionDecision.Decision.APPROVED, Instant.parse("2026-10-05T10:00:00Z"),
                "reviewer", "Approved after review", "decision-1");

        ScopeQuantityRepository quantities = new Quantities(List.of(original, replacement));
        QuantityCorrectionRepository corrections = new Corrections(List.of(correction));
        QuantityCorrectionDecisionRepository decisions = new Decisions(List.of(approval));
        var useCase = new GetAcceptedScopeQuantity(quantities, corrections, decisions);

        var historical = useCase.execute(tenant, definition, LocalDate.parse("2026-10-03"));
        assertEquals(original.id(), historical.acceptedMeasurementId());
        assertEquals(new BigDecimal("3"), historical.acceptedQuantity());
        assertEquals(List.of(correction.id()), historical.pendingCorrectionIds());

        var current = useCase.execute(tenant, definition);
        assertEquals(replacement.id(), current.acceptedMeasurementId());
        assertEquals(new BigDecimal("4"), current.acceptedQuantity());
    }

    private QuantityMeasurement measurement(UUID tenant, UUID definition, String amount, String date, String key) {
        return new QuantityMeasurement(UUID.randomUUID(), tenant, definition, LocalDate.parse(date), new BigDecimal(amount),
                Instant.parse(date + "T09:00:00Z"), "surveyor", key, null);
    }

    private record Quantities(List<QuantityMeasurement> history) implements ScopeQuantityRepository {
        public boolean createDefinition(ScopeQuantityDefinition d) { return false; }
        public Optional<ScopeQuantityDefinition> findDefinition(UUID t, UUID d) { return Optional.empty(); }
        public List<ScopeQuantityDefinition> findDefinitionsForScope(UUID t, String type, String id) { return List.of(); }
        public boolean appendMeasurement(QuantityMeasurement m) { return false; }
        public List<QuantityMeasurement> findMeasurementHistory(UUID t, UUID d) {
            return history.stream().filter(m -> m.tenantId().equals(t) && m.definitionId().equals(d)).toList();
        }
        public Optional<QuantityMeasurement> findLatestMeasurementAsOf(UUID t, UUID d, LocalDate date) { return Optional.empty(); }
    }
    private record Corrections(List<QuantityMeasurementCorrection> history) implements QuantityCorrectionRepository {
        public boolean appendCorrection(QuantityMeasurementCorrection c) { return false; }
        public List<QuantityMeasurementCorrection> findCorrections(UUID t, UUID d, UUID original) {
            return history.stream().filter(c -> c.tenantId().equals(t) && c.definitionId().equals(d)
                    && c.originalMeasurementId().equals(original)).toList();
        }
        public List<QuantityMeasurementCorrection> findCorrectionsForDefinition(UUID t, UUID d) {
            return history.stream().filter(c -> c.tenantId().equals(t) && c.definitionId().equals(d)).toList();
        }
    }
    private record Decisions(List<QuantityCorrectionDecision> history) implements QuantityCorrectionDecisionRepository {
        public boolean appendDecision(QuantityCorrectionDecision d) { return false; }
        public List<QuantityCorrectionDecision> findDecisions(UUID t, UUID d) {
            return history.stream().filter(v -> v.tenantId().equals(t) && v.definitionId().equals(d)).toList();
        }
    }
}
