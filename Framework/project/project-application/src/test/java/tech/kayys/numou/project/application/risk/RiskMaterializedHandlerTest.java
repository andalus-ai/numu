package tech.kayys.syirkah.project.application.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.project.application.support.InMemoryIssueRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectRepository;
import tech.kayys.syirkah.project.application.support.InMemoryRiskRepository;
import tech.kayys.syirkah.project.application.risk.policy.DefaultRiskMaterializationPolicy;
import tech.kayys.syirkah.project.application.risk.policy.RiskMaterializedHandler;
import tech.kayys.syirkah.project.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectNumber;
import tech.kayys.syirkah.project.domain.project.ProjectType;
import tech.kayys.syirkah.project.domain.risk.IssueId;
import tech.kayys.syirkah.project.domain.risk.IssuePriority;
import tech.kayys.syirkah.project.domain.risk.IssueSeverity;
import tech.kayys.syirkah.project.domain.risk.Risk;
import tech.kayys.syirkah.project.domain.risk.RiskCategory;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.RiskResponse;
import tech.kayys.syirkah.project.domain.risk.RiskSource;
import tech.kayys.syirkah.project.domain.risk.event.IssueRaised;
import tech.kayys.syirkah.project.domain.risk.event.RiskMaterialized;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RiskMaterializedHandler")
class RiskMaterializedHandlerTest {

    private static final ProjectId PROJECT = ProjectId.generate();
    private static final LocalDate IDENTIFIED_ON = LocalDate.of(2026, 1, 1);
    private static final LocalDate TARGET = LocalDate.of(2026, 6, 30);

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryRiskRepository risks = new InMemoryRiskRepository();
    private final InMemoryIssueRepository issues = new InMemoryIssueRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private final RiskMaterializedHandler handler =
            new RiskMaterializedHandler(issues,
                    new DefaultRiskMaterializationPolicy(), events);

    @BeforeEach
    void seedProject() {
        projects.save(Project.create(
                PROJECT, ProjectNumber.of("PRJ-001"), "Test",
                ProjectType.CLIENT_BILLABLE, null));
    }

    @Test
    @DisplayName("materialized risk raises an issue with derived severity/priority")
    void materializedRiskRaisesIssue() {
        var riskId = RiskId.generate();
        var risk = Risk.identify(
                riskId, PROJECT, "R-001", "Concrete supplier may slip",
                "Cement delivery delay",
                RiskCategory.SUPPLIER, RiskSource.SUPPLIER,
                IDENTIFIED_ON, TARGET
        );
        risk.assess(tech.kayys.syirkah.project.domain.risk.Probability.VERY_HIGH,
                tech.kayys.syirkah.project.domain.risk.ImpactLevel.CRITICAL);
        risk.planResponse(RiskResponse.MITIGATE);
        risk.monitor();
        risk.materialize();

        var event = risk.pullDomainEvents().stream()
                .filter(e -> e instanceof RiskMaterialized)
                .map(e -> (RiskMaterialized) e)
                .findFirst().orElseThrow();

        var issueId = handler.handle(event).await().indefinitely();

        var saved = issues.findById(issueId)
                .toCompletableFuture().join().orElseThrow();

        assertEquals(riskId, saved.sourceRiskId());
        assertEquals("R-001-ISSUE", saved.number());
        assertEquals(IssueSeverity.CRITICAL, saved.severity());
        assertEquals(IssuePriority.URGENT, saved.priority());
        assertTrue(events.publishedTypes().contains(IssueRaised.class));
    }

    @Test
    @DisplayName("replaying the event is idempotent")
    void replayIsIdempotent() {
        var riskId = RiskId.generate();
        var risk = Risk.identify(
                riskId, PROJECT, "R-002", "Windstorm",
                "Hurricane risk",
                RiskCategory.QUALITY, RiskSource.MARKET,
                IDENTIFIED_ON, TARGET
        );
        risk.assess(tech.kayys.syirkah.project.domain.risk.Probability.HIGH,
                tech.kayys.syirkah.project.domain.risk.ImpactLevel.MAJOR);
        risk.planResponse(RiskResponse.TRANSFER);
        risk.monitor();
        risk.materialize();

        var event = risk.pullDomainEvents().stream()
                .filter(e -> e instanceof RiskMaterialized)
                .map(e -> (RiskMaterialized) e)
                .findFirst().orElseThrow();

        var first = handler.handle(event).await().indefinitely();
        var second = handler.handle(event).await().indefinitely();

        assertEquals(first, second);
        assertEquals(1, issues.findByRiskId(riskId)
                .toCompletableFuture().join().size());
    }
}