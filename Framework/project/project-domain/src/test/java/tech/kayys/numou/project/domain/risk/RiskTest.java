package tech.kayys.syirkah.project.domain.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.event.RiskAssessed;
import tech.kayys.syirkah.project.domain.risk.event.RiskClosed;
import tech.kayys.syirkah.project.domain.risk.event.RiskIdentified;
import tech.kayys.syirkah.project.domain.risk.event.RiskMaterialized;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Risk aggregate")
class RiskTest {

    private static final ProjectId PROJECT = ProjectId.generate();
    private static final LocalDate IDENTIFIED_ON = LocalDate.of(2026, 1, 1);
    private static final LocalDate TARGET = LocalDate.of(2026, 6, 30);

    private static Risk risk() {
        return Risk.identify(
                RiskId.generate(),
                PROJECT,
                "R-001",
                "Concrete supplier may slip",
                "Cement delivery delay",
                RiskCategory.SUPPLIER,
                RiskSource.SUPPLIER,
                IDENTIFIED_ON,
                TARGET
        );
    }

    @Test
    void identifyStartsAsIdentifiedAndRaisesEvent() {
        var risk = risk();

        assertEquals(RiskStatus.IDENTIFIED, risk.status());
        assertNull(risk.score());
        assertEquals("R-001", risk.number());
        assertTrue(risk.pullDomainEvents().getFirst() instanceof RiskIdentified);
    }

    @Test
    void assessCalculatesScoreNeverStoresIt() {
        var risk = risk();

        risk.assess(Probability.HIGH, ImpactLevel.MAJOR);

        assertEquals(RiskStatus.ASSESSED, risk.status());
        assertEquals(16, risk.score().score());
        assertTrue(risk.score().isHigh());
        assertFalse(risk.score().isCritical());
        assertTrue(risk.pullDomainEvents().stream()
                .anyMatch(RiskAssessed.class::isInstance));
    }

    @Test
    void fullLifecycleRaisesEachEvent() {
        var risk = risk();

        risk.assess(Probability.VERY_HIGH, ImpactLevel.CRITICAL);
        risk.planResponse(RiskResponse.MITIGATE);
        risk.monitor();
        risk.materialize();
        risk.close();

        assertEquals(RiskStatus.CLOSED, risk.status());

        var events = risk.pullDomainEvents();
        assertTrue(events.stream().anyMatch(RiskAssessed.class::isInstance));
        assertTrue(events.stream().anyMatch(RiskMaterialized.class::isInstance));
        assertTrue(events.stream().anyMatch(RiskClosed.class::isInstance));

        // The materialized event is self-sufficient for the policy.
        var materialized = events.stream()
                .filter(event -> event instanceof RiskMaterialized)
                .map(event -> (RiskMaterialized) event)
                .findFirst().orElseThrow();
        assertEquals("R-001", materialized.number());
        assertEquals("Concrete supplier may slip", materialized.title());
        assertEquals(25, materialized.score().score());
    }

    @Test
    void illegalTransitionsAreRejected() {
        var risk = risk();

        assertThrows(InvalidRiskStateException.class,
                () -> risk.planResponse(RiskResponse.AVOID));
        assertThrows(InvalidRiskStateException.class, risk::monitor);
        assertThrows(InvalidRiskStateException.class, risk::materialize);

        risk.assess(Probability.LOW, ImpactLevel.MINOR);
        assertThrows(InvalidRiskStateException.class, risk::monitor);

        risk.planResponse(RiskResponse.ACCEPT);
        risk.close();

        assertThrows(InvalidRiskStateException.class, risk::close);
        assertThrows(InvalidRiskStateException.class,
                () -> risk.assess(Probability.HIGH, ImpactLevel.MAJOR));
    }

    @Test
    void rejectedConstruction() {
        assertThrows(IllegalArgumentException.class, () -> Risk.identify(
                RiskId.generate(), PROJECT, "R-002", "  ", "desc",
                RiskCategory.COST, RiskSource.INTERNAL,
                IDENTIFIED_ON, TARGET));

        assertThrows(IllegalArgumentException.class, () -> Risk.identify(
                RiskId.generate(), PROJECT, "R-002", "title", "desc",
                RiskCategory.COST, RiskSource.INTERNAL,
                TARGET, IDENTIFIED_ON));
    }

    @Test
    void riskScoreInvariantCannotBeBroken() {
        var score = RiskScore.of(Probability.VERY_HIGH, ImpactLevel.CRITICAL);

        assertEquals(25, score.score());
        assertTrue(score.isCritical());

        assertThrows(IllegalArgumentException.class,
                () -> new RiskScore(5, 5, 7));
        assertThrows(IllegalArgumentException.class,
                () -> new RiskScore(0, 5, 0));
    }
}
