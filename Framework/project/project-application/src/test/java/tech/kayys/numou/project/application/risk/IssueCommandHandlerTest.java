package tech.kayys.syirkah.project.application.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.support.InMemoryIssueRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectRepository;
import tech.kayys.syirkah.project.application.support.InMemoryRiskRepository;
import tech.kayys.syirkah.project.application.risk.command.CloseIssueCommand;
import tech.kayys.syirkah.project.application.risk.command.InvestigateIssueCommand;
import tech.kayys.syirkah.project.application.risk.command.PlanIssueActionCommand;
import tech.kayys.syirkah.project.application.risk.command.RaiseIssueCommand;
import tech.kayys.syirkah.project.application.risk.command.RejectIssueCommand;
import tech.kayys.syirkah.project.application.risk.command.ResolveIssueCommand;
import tech.kayys.syirkah.project.application.risk.command.StartIssueWorkCommand;
import tech.kayys.syirkah.project.application.risk.handler.ChangeIssueStateHandler;
import tech.kayys.syirkah.project.application.risk.handler.RaiseIssueHandler;
import tech.kayys.syirkah.project.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectNumber;
import tech.kayys.syirkah.project.domain.project.ProjectType;
import tech.kayys.syirkah.project.domain.risk.IssueId;
import tech.kayys.syirkah.project.domain.risk.IssuePriority;
import tech.kayys.syirkah.project.domain.risk.IssueSeverity;
import tech.kayys.syirkah.project.domain.risk.IssueStatus;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.domain.risk.event.IssueClosed;
import tech.kayys.syirkah.project.domain.risk.event.IssueRaised;
import tech.kayys.syirkah.project.domain.risk.event.IssueResolved;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("Issue command handlers")
class IssueCommandHandlerTest {

    private static final ProjectId PROJECT = ProjectId.generate();

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryRiskRepository risks = new InMemoryRiskRepository();
    private final InMemoryIssueRepository issues = new InMemoryIssueRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private final RaiseIssueHandler raise =
            new RaiseIssueHandler(projects, risks, issues, events);
    private final ChangeIssueStateHandler change =
            new ChangeIssueStateHandler(issues, events);

    private IssueId registerProjectAndIssue(String number) {
        projects.save(Project.create(
                PROJECT, ProjectNumber.of("PRJ-001"), "Test",
                ProjectType.CLIENT_BILLABLE, null));

        return raise.handle(new RaiseIssueCommand(
                PROJECT, null, number, "Crane stopped mid-lift",
                "Hydraulic failure",
                IssueSeverity.HIGH, IssuePriority.HIGH,
                UUID.randomUUID()
        )).await().indefinitely().orElseThrow();
    }

    @Test
    @DisplayName("raise persists the issue and publishes IssueRaised")
    void raisePersistsAndPublishes() {
        var issueId = registerProjectAndIssue("ISS-001");

        var saved = issues.findById(issueId)
                .toCompletableFuture().join().orElseThrow();

        assertEquals("ISS-001", saved.number());
        assertTrue(events.publishedTypes().contains(IssueRaised.class));
    }

    @Test
    @DisplayName("the full issue lifecycle publishes IssueResolved then IssueClosed")
    void fullLifecyclePublishesInOrder() {
        var issueId = registerProjectAndIssue("ISS-001");

        events.reset();
        change.investigate(new InvestigateIssueCommand(issueId))
                .await().indefinitely().orElseThrow();
        change.planAction(new PlanIssueActionCommand(issueId))
                .await().indefinitely().orElseThrow();
        change.startWork(new StartIssueWorkCommand(issueId))
                .await().indefinitely().orElseThrow();
        change.resolve(new ResolveIssueCommand(
                issueId, "Hydraulic hose burst",
                "Replace hoses + PM schedule"
        )).await().indefinitely().orElseThrow();
        change.close(new CloseIssueCommand(issueId))
                .await().indefinitely().orElseThrow();

        assertEquals(List.of(
                IssueResolved.class,
                IssueClosed.class
        ), events.publishedTypes());
    }

    @Test
    @DisplayName("an unknown issue yields a typed ISSUE_NOT_FOUND failure")
    void unknownIssueReturnsNotFound() {
        var result = change.investigate(new InvestigateIssueCommand(
                IssueId.generate()
        )).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals(RiskErrors.ISSUE_NOT_FOUND, errorOf(result).code());
    }

    @Test
    @DisplayName("rejecting an issue from OPEN is allowed and publishes nothing")
    void rejectFromOpenIsAllowed() {
        var issueId = registerProjectAndIssue("ISS-001");

        events.reset();
        change.reject(new RejectIssueCommand(issueId))
                .await().indefinitely().orElseThrow();

        var saved = issues.findById(issueId)
                .toCompletableFuture().join().orElseThrow();

        assertEquals(
                IssueStatus.REJECTED,
                saved.status()
        );
        assertTrue(events.publishedTypes().isEmpty());
    }

    private static ApplicationError errorOf(Result<?> result) {
        return assertInstanceOf(Result.Failure.class, result).error();
    }
}