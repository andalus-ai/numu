package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.risk.Issue;
import tech.kayys.numou.project.domain.risk.IssueSeverity;
import tech.kayys.numou.project.domain.risk.RiskActionStatus;
import tech.kayys.numou.project.domain.risk.RiskStatus;
import tech.kayys.numou.project.spi.port.IssueRepository;
import tech.kayys.numou.project.spi.port.ProjectRiskQueryRepository;
import tech.kayys.numou.project.spi.port.ProjectRiskSummary;
import tech.kayys.numou.project.spi.port.RiskRegisterRow;
import tech.kayys.numou.project.spi.port.RiskTreatmentActionRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * In-memory read model for the risk dashboard.
 *
 * v1 computes the counters from the in-memory stores; the production
 * adapter projects the same figures from SQL. Band semantics:
 * critical = score &ge; 20, high = 12–19 (and critical is counted in
 * {@code criticalRisks} only, never double-counted as high).
 */
public final class InMemoryProjectRiskQueryRepository
        implements ProjectRiskQueryRepository {

    private final InMemoryRiskRepository risks;
    private final RiskTreatmentActionRepository actions;
    private final IssueRepository issues;
    private final Supplier<LocalDate> today;

    public InMemoryProjectRiskQueryRepository(
            InMemoryRiskRepository risks,
            RiskTreatmentActionRepository actions,
            IssueRepository issues,
            Supplier<LocalDate> today
    ) {
        this.risks = Objects.requireNonNull(risks);
        this.actions = Objects.requireNonNull(actions);
        this.issues = Objects.requireNonNull(issues);
        this.today = Objects.requireNonNull(today);
    }

    @Override
    public CompletionStage<ProjectRiskSummary> findSummary(ProjectId projectId) {
        var allRisks = risks.findAllByProjectId(projectId);

        long total = allRisks.size();
        long open = allRisks.stream()
                .filter(risk -> risk.status() != RiskStatus.CLOSED)
                .count();
        long critical = allRisks.stream()
                .filter(risk -> risk.status() != RiskStatus.CLOSED)
                .filter(risk -> risk.score() != null && risk.score().isCritical())
                .count();
        long high = allRisks.stream()
                .filter(risk -> risk.status() != RiskStatus.CLOSED)
                .filter(risk -> risk.score() != null
                        && risk.score().isHigh()
                        && !risk.score().isCritical())
                .count();
        long materialized = allRisks.stream()
                .filter(risk -> risk.status() == RiskStatus.MATERIALIZED)
                .count();

        var openIssues = issues.findOpenByProjectId(projectId)
                .toCompletableFuture().join();
        long openIssueCount = openIssues.size();
        long criticalIssues = openIssues.stream()
                .filter(issue -> issue.severity() == IssueSeverity.CRITICAL)
                .count();

        var openActions = actions.findOpenByProjectId(projectId)
                .toCompletableFuture().join();
        long overdueActions = openActions.stream()
                .filter(action -> action.status() != RiskActionStatus.COMPLETED
                        && action.status() != RiskActionStatus.CANCELLED)
                .filter(action -> action.dueDate().isBefore(today.get()))
                .count();

        return CompletableFuture.completedFuture(new ProjectRiskSummary(
                projectId,
                total,
                open,
                high,
                critical,
                materialized,
                openIssueCount,
                criticalIssues,
                overdueActions
        ));
    }

    @Override
    public CompletionStage<List<RiskRegisterRow>> findRiskRegister(
            ProjectId projectId
    ) {
        var rows = risks.findAllByProjectId(projectId).stream()
                .sorted(Comparator.comparing(
                        risk -> risk.number()))
                .map(risk -> new RiskRegisterRow(
                        risk.id(),
                        risk.projectId(),
                        risk.number(),
                        risk.title(),
                        risk.category(),
                        risk.status(),
                        risk.probability(),
                        risk.impact(),
                        risk.score() == null ? 0 : risk.score().score(),
                        risk.response(),
                        risk.targetDate()
                ))
                .toList();

        return CompletableFuture.completedFuture(rows);
    }

    @Override
    public CompletionStage<List<Issue>> findOpenIssues(ProjectId projectId) {
        return issues.findOpenByProjectId(projectId);
    }
}
