package tech.kayys.syirkah.project.application.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.support.InMemoryIssueRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectRepository;
import tech.kayys.syirkah.project.application.support.InMemoryRiskRepository;
import tech.kayys.syirkah.project.application.risk.command.AssessRiskCommand;
import tech.kayys.syirkah.project.application.risk.command.CloseRiskCommand;
import tech.kayys.syirkah.project.application.risk.command.IdentifyRiskCommand;
import tech.kayys.syirkah.project.application.risk.command.MaterializeRiskCommand;
import tech.kayys.syirkah.project.application.risk.command.PlanRiskResponseCommand;
import tech.kayys.syirkah.project.application.risk.command.StartRiskMonitoringCommand;
import tech.kayys.syirkah.project.application.risk.handler.ChangeRiskStateHandler;
import tech.kayys.syirkah.project.application.risk.handler.IdentifyRiskHandler;
import tech.kayys.syirkah.project.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectNumber;
import tech.kayys.syirkah.project.domain.project.ProjectType;
import tech.kayys.syirkah.project.domain.risk.ImpactLevel;
import tech.kayys.syirkah.project.domain.risk.Probability;
import tech.kayys.syirkah.project.domain.risk.RiskCategory;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskResponse;
import tech.kayys.syirkah.project.domain.risk.RiskSource;
import tech.kayys.syirkah.project.domain.risk.RiskStatus;
import tech.kayys.syirkah.project.domain.risk.event.RiskAssessed;
import tech.kayys.syirkah.project.domain.risk.event.RiskClosed;
import tech.kayys.syirkah.project.domain.risk.event.RiskIdentified;
import tech.kayys.syirkah.project.domain.risk.event.RiskMaterialized;
import tech.kayys.syirkah.project.domain.risk.event.RiskMonitoringStarted;
import tech.kayys.syirkah.project.domain.risk.event.RiskResponsePlanned;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("Risk command handlers")
class RiskCommandHandlerTest {

    private static final ProjectId PROJECT = ProjectId.generate();
    private static final LocalDate IDENTIFIED_ON = LocalDate.of(2026, 1, 1);
    private static final LocalDate TARGET = LocalDate.of(2026, 6, 30);

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryRiskRepository risks = new InMemoryRiskRepository();
    private final InMemoryIssueRepository issues = new InMemoryIssueRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private final IdentifyRiskHandler identify =
            new IdentifyRiskHandler(projects, risks, events);
    private final ChangeRiskStateHandler change =
            new ChangeRiskStateHandler(risks, events);

    private RiskId registerProjectAndRisk(String number) {
        projects.save(Project.create(
                PROJECT, ProjectNumber.of("PRJ-001"), "Test",
                ProjectType.CLIENT_BILLABLE, null));

        return identify.handle(new IdentifyRiskCommand(
                PROJECT, number, "Concrete supplier may slip",
                "Cement delivery delay",
                RiskCategory.SUPPLIER, RiskSource.SUPPLIER,
                IDENTIFIED_ON, TARGET
        )).await().indefinitely().orElseThrow();
    }

    @Test
    @DisplayName("identify persists the risk and publishes RiskIdentified")
    void identifyPersistsAndPublishes() {
        var riskId = registerProjectAndRisk("R-001");

        var saved = risks.findById(riskId)
                .toCompletableFuture().join().orElseThrow();

        assertEquals("R-001", saved.number());
        assertEquals(RiskStatus.IDENTIFIED, saved.status());
        assertTrue(events.publishedTypes().contains(RiskIdentified.class));
    }

    @Test
    @DisplayName("duplicate risk number is rejected")
    void duplicateRiskNumberIsRejected() {
        registerProjectAndRisk("R-001");

        var result = identify.handle(new IdentifyRiskCommand(
                PROJECT, "R-001", "Other", "desc",
                RiskCategory.COST, RiskSource.INTERNAL,
                IDENTIFIED_ON, TARGET
        )).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals(RiskErrors.DUPLICATE_RISK_NUMBER,
                errorOf(result).code());
    }

    @Test
    @DisplayName("assess derives the score and publishes RiskAssessed")
    void assessDerivesScore() {
        var riskId = registerProjectAndRisk("R-001");

        events.reset();
        change.assess(new AssessRiskCommand(
                riskId, Probability.HIGH, ImpactLevel.MAJOR
        )).await().indefinitely().orElseThrow();

        var saved = risks.findById(riskId)
                .toCompletableFuture().join().orElseThrow();

        assertEquals(RiskStatus.ASSESSED, saved.status());
        assertEquals(16, saved.score().score());
        assertTrue(saved.score().isHigh());
        assertTrue(events.publishedTypes().contains(RiskAssessed.class));
    }

    @Test
    @DisplayName("the full risk lifecycle publishes one event per transition")
    void fullLifecyclePublishesInOrder() {
        var riskId = registerProjectAndRisk("R-001");

        change.assess(new AssessRiskCommand(
                riskId, Probability.VERY_HIGH, ImpactLevel.CRITICAL
        )).await().indefinitely().orElseThrow();
        events.reset();

        change.planResponse(new PlanRiskResponseCommand(
                riskId, RiskResponse.MITIGATE
        )).await().indefinitely().orElseThrow();
        change.startMonitoring(new StartRiskMonitoringCommand(riskId))
                .await().indefinitely().orElseThrow();
        change.materialize(new MaterializeRiskCommand(riskId))
                .await().indefinitely().orElseThrow();
        change.close(new CloseRiskCommand(riskId))
                .await().indefinitely().orElseThrow();

        var saved = risks.findById(riskId)
                .toCompletableFuture().join().orElseThrow();

        assertEquals(RiskStatus.CLOSED, saved.status());
        assertEquals(List.of(
                RiskResponsePlanned.class,
                RiskMonitoringStarted.class,
                RiskMaterialized.class,
                RiskClosed.class
        ), events.publishedTypes());
    }

    @Test
    @DisplayName("an unknown risk yields a typed RISK_NOT_FOUND failure")
    void unknownRiskReturnsNotFound() {
        var result = change.assess(new AssessRiskCommand(
                RiskId.generate(), Probability.LOW, ImpactLevel.MINOR
        )).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals(RiskErrors.RISK_NOT_FOUND, errorOf(result).code());
    }

    private static ApplicationError errorOf(Result<?> result) {
        return assertInstanceOf(Result.Failure.class, result).error();
    }
}