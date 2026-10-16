package tech.kayys.syirkah.project.domain.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.event.IssueClosed;
import tech.kayys.syirkah.project.domain.risk.event.IssueRaised;
import tech.kayys.syirkah.project.domain.risk.event.IssueResolved;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Issue aggregate")
class IssueTest {

    private static final ProjectId PROJECT = ProjectId.generate();

    private static Issue issue() {
        return Issue.raise(
                IssueId.generate(),
                PROJECT,
                null,
                "ISS-001",
                "Crane stopped mid-lift",
                "Hydraulic failure",
                IssueSeverity.HIGH,
                IssuePriority.HIGH,
                null
        );
    }

    @Test
    void raiseStartsOpenWithoutSourceRisk() {
        var issue = issue();

        assertEquals(IssueStatus.OPEN, issue.status());
        assertNull(issue.sourceRiskId());
        assertTrue(issue.pullDomainEvents().getFirst() instanceof IssueRaised);
    }

    @Test
    void fullLifecycleRaisesResolvedAndClosedEvents() {
        var issue = issue();

        issue.investigate();
        issue.planAction();
        issue.startWork();
        issue.resolve("Hydraulic hose burst", "Replace hoses + PM schedule");
        issue.close();

        assertEquals(IssueStatus.CLOSED, issue.status());
        assertEquals("Hydraulic hose burst", issue.rootCause());
        assertEquals("Replace hoses + PM schedule", issue.resolution());

        var events = issue.pullDomainEvents();
        assertTrue(events.stream().anyMatch(IssueResolved.class::isInstance));
        assertTrue(events.stream().anyMatch(IssueClosed.class::isInstance));
    }

    @Test
    void illegalTransitionsAreRejected() {
        var issue = issue();

        assertThrows(InvalidIssueStateException.class, issue::planAction);
        assertThrows(InvalidIssueStateException.class, issue::startWork);
        assertThrows(InvalidIssueStateException.class,
                () -> issue.resolve("cause", "fix"));
        assertThrows(InvalidIssueStateException.class, issue::close);

        issue.investigate();
        assertThrows(InvalidIssueStateException.class, issue::investigate);

        issue.planAction();
        assertThrows(InvalidIssueStateException.class, issue::reject);
    }

    @Test
    void resolveRequiresRootCauseAndResolution() {
        var issue = issue();
        issue.investigate();
        issue.planAction();
        issue.startWork();

        assertThrows(IllegalArgumentException.class,
                () -> issue.resolve("  ", "fix"));
        assertThrows(IllegalArgumentException.class,
                () -> issue.resolve("cause", " "));
    }

    @Test
    void materializedIssueCarriesRiskLink() {
        var riskId = RiskId.generate();
        var issue = Issue.fromMaterializedRisk(
                IssueId.generate(), PROJECT, riskId, "R-001-ISSUE",
                "Risk materialized: Delay", "desc",
                IssueSeverity.CRITICAL, IssuePriority.URGENT, null);

        assertEquals(riskId, issue.sourceRiskId());
        assertEquals(IssueStatus.OPEN, issue.status());

        issue.reject();
        assertEquals(IssueStatus.REJECTED, issue.status());
    }
}
