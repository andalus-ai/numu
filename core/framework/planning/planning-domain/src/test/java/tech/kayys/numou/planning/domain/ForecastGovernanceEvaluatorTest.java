package tech.kayys.numou.planning.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ForecastGovernanceEvaluatorTest {
    private final ForecastGovernanceEvaluator evaluator = new ForecastGovernanceEvaluator();

    @Test
    void reportsStaleEvidenceAndUnsupportedMethodDeterministically() {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID(), node = UUID.randomUUID();
        PlanId plan = PlanId.generate();
        var forecast = forecast(tenant, structure, node, LocalDate.of(2026, 1, 1), "legacy-v0");
        var risk = risk(tenant, structure, node, plan, Optional.of(forecast));
        var report = evaluator.evaluate(tenant, structure, plan, LocalDate.of(2026, 1, 15),
                7, Set.of("linear-regression-progress-v1"), List.of(risk));
        assertTrue(report.hasWarnings());
        assertFalse(report.hasErrors());
        assertEquals(1, report.count(ForecastGovernanceFinding.Code.STALE_PROGRESS_EVIDENCE));
        assertEquals(1, report.count(ForecastGovernanceFinding.Code.UNSUPPORTED_FORECAST_METHOD));
        assertEquals(2, report.findings().size());
    }

    @Test
    void reportsFutureEvidenceAndScopeMismatch() {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID(), node = UUID.randomUUID();
        PlanId plan = PlanId.generate();
        var future = forecast(tenant, structure, node, LocalDate.of(2026, 1, 12), "linear-regression-progress-v1");
        var risk = risk(tenant, structure, node, PlanId.generate(), Optional.of(future));
        var report = evaluator.evaluate(tenant, structure, plan, LocalDate.of(2026, 1, 10),
                7, Set.of("linear-regression-progress-v1"), List.of(risk));
        assertTrue(report.hasErrors());
        assertEquals(1, report.count(ForecastGovernanceFinding.Code.SCOPE_MISMATCH));
        assertFalse(report.findings().stream().anyMatch(f ->
                f.code() == ForecastGovernanceFinding.Code.FUTURE_PROGRESS_EVIDENCE));
    }

    @Test
    void reportsMissingProgressEvidenceAsInformational() {
        UUID tenant = UUID.randomUUID(), structure = UUID.randomUUID(), node = UUID.randomUUID();
        PlanId plan = PlanId.generate();
        var report = evaluator.evaluate(tenant, structure, plan, LocalDate.of(2026, 1, 10),
                7, Set.of("linear-regression-progress-v1"),
                List.of(risk(tenant, structure, node, plan, Optional.empty())));
        assertFalse(report.hasErrors());
        assertFalse(report.hasWarnings());
        assertEquals(1, report.count(ForecastGovernanceFinding.Code.MISSING_PROGRESS_EVIDENCE));
    }

    @Test
    void rejectsNegativeFreshnessWindow() {
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(UUID.randomUUID(),
                UUID.randomUUID(), PlanId.generate(), LocalDate.now(), -1, Set.of(), List.of()));
    }

    private ProgressForecast forecast(UUID tenant, UUID structure, UUID node, LocalDate asOf, String method) {
        return new ProgressForecast(tenant, structure, node, UUID.randomUUID(), asOf,
                asOf.plusDays(10), asOf.plusDays(12), new BigDecimal("50"),
                new BigDecimal("5"), 2, 2, method);
    }
    private WorkBreakdownScheduleRisk risk(UUID tenant, UUID structure, UUID node, PlanId plan,
            Optional<ProgressForecast> progress) {
        return new WorkBreakdownScheduleRisk(tenant, structure, node, plan,
                List.of(new ActivityId(UUID.randomUUID())), 0,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), progress);
    }
}
