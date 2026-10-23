package tech.kayys.numou.planning.adapter.postgres;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.domain.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JdbcQuantityCorrectionDecisionRepositoryIntegrationTest {
    @Inject JdbcScopeQuantityRepository quantities;
    @Inject JdbcQuantityCorrectionRepository corrections;
    @Inject JdbcQuantityCorrectionDecisionRepository decisions;

    @Test void reviewDecisionIsTenantScopedAppendOnlyAndIdempotent() {
        UUID tenant = UUID.randomUUID(), definitionId = UUID.randomUUID();
        assertTrue(quantities.createDefinition(new ScopeQuantityDefinition(definitionId, tenant,
                "DELIVERABLE", "review-test-" + definitionId, "COUNT", "Reviewed units", "unit",
                new BigDecimal("10"), 1)));
        var original = measurement(tenant, definitionId, "original", "2");
        var replacement = measurement(tenant, definitionId, "replacement", "3");
        assertTrue(quantities.appendMeasurement(original));
        assertTrue(quantities.appendMeasurement(replacement));
        var correction = new QuantityMeasurementCorrection(UUID.randomUUID(), tenant, definitionId,
                original.id(), replacement.id(), Instant.parse("2026-10-22T09:00:00Z"),
                "planner", "Correction from verified source", "corr-" + UUID.randomUUID());
        assertTrue(corrections.appendCorrection(correction));

        var decision = new QuantityCorrectionDecision(UUID.randomUUID(), tenant, definitionId, correction.id(),
                QuantityCorrectionDecision.Decision.APPROVED, Instant.parse("2026-10-23T09:00:00Z"),
                "reviewer", "Source evidence verified", "decision-" + UUID.randomUUID());
        assertTrue(decisions.appendDecision(decision));
        assertFalse(decisions.appendDecision(decision));
        assertEquals(1, decisions.findDecisions(tenant, definitionId).stream()
                .filter(d -> d.id().equals(decision.id())).count());
        assertTrue(decisions.findDecisions(UUID.randomUUID(), definitionId).isEmpty());
    }

    private QuantityMeasurement measurement(UUID tenant, UUID definition, String key, String amount) {
        return new QuantityMeasurement(UUID.randomUUID(), tenant, definition, LocalDate.of(2026, 10, 22),
                new BigDecimal(amount), Instant.parse("2026-10-22T10:00:00Z"), "planner",
                key + "-" + UUID.randomUUID(), null);
    }
}
