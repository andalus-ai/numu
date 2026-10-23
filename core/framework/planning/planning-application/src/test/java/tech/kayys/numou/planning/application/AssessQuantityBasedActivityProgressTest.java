package tech.kayys.numou.planning.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.*;
import tech.kayys.numou.planning.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssessQuantityBasedActivityProgressTest {
    @Test void refusesActivityLinkedToAnotherScope() {
        UUID tenant = UUID.randomUUID(), definitionId = UUID.randomUUID();
        ScopeQuantityRepository repository = new StubQuantityRepository(new ScopeQuantityDefinition(definitionId, tenant,
                "DELIVERABLE", "scope-1", "QTY", "Units", "unit", new BigDecimal("10"), 1));
        var accepted = new GetAcceptedScopeQuantity(repository, new EmptyCorrectionRepository(), new EmptyDecisionRepository());
        var useCase = new AssessQuantityBasedActivityProgress(repository, accepted);
        var activity = ScheduleActivity.create("A-1", "Wrong scope", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-10"), new WorkItemReference("DELIVERABLE", "scope-2"), 0);
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(tenant, definitionId, activity, LocalDate.parse("2026-10-05")));
    }

    private static class StubQuantityRepository implements ScopeQuantityRepository {
        private final ScopeQuantityDefinition definition;
        StubQuantityRepository(ScopeQuantityDefinition definition) { this.definition = definition; }
        public boolean createDefinition(ScopeQuantityDefinition d) { return false; }
        public Optional<ScopeQuantityDefinition> findDefinition(UUID t, UUID id) {
            return t.equals(definition.tenantId()) && id.equals(definition.id()) ? Optional.of(definition) : Optional.empty();
        }
        public List<ScopeQuantityDefinition> findDefinitionsForScope(UUID t, String type, String id) { return List.of(); }
        public boolean appendMeasurement(QuantityMeasurement m) { return false; }
        public List<QuantityMeasurement> findMeasurementHistory(UUID t, UUID d) { return List.of(); }
        public Optional<QuantityMeasurement> findLatestMeasurementAsOf(UUID t, UUID d, LocalDate date) { return Optional.empty(); }
    }
    private static class EmptyCorrectionRepository implements QuantityCorrectionRepository {
        public boolean appendCorrection(QuantityMeasurementCorrection c) { return false; }
        public List<QuantityMeasurementCorrection> findCorrections(UUID t, UUID d, UUID original) { return List.of(); }
        public List<QuantityMeasurementCorrection> findCorrectionsForDefinition(UUID t, UUID d) { return List.of(); }
    }
    private static class EmptyDecisionRepository implements QuantityCorrectionDecisionRepository {
        public boolean appendDecision(QuantityCorrectionDecision d) { return false; }
        public List<QuantityCorrectionDecision> findDecisions(UUID t, UUID d) { return List.of(); }
    }
}
