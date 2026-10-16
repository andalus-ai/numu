package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.Issue;
import tech.kayys.syirkah.project.domain.risk.IssueId;
import tech.kayys.syirkah.project.domain.risk.IssueStatus;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.spi.port.IssueRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Test double for {@link IssueRepository}. */
public final class InMemoryIssueRepository implements IssueRepository {

    private final Map<IssueId, Issue> issuesById = new LinkedHashMap<>();

    @Override
    public CompletionStage<Issue> save(Issue aggregate) {
        issuesById.put(aggregate.id(), aggregate);

        return CompletableFuture.completedFuture(aggregate);
    }

    @Override
    public CompletionStage<Optional<Issue>> findById(IssueId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(issuesById.get(id))
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(IssueId id) {
        return CompletableFuture.completedFuture(issuesById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Issue aggregate) {
        issuesById.remove(aggregate.id());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(IssueId id) {
        issuesById.remove(id);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Optional<Issue>> findByNumber(
            ProjectId projectId, String number
    ) {
        return CompletableFuture.completedFuture(
                issuesById.values().stream()
                        .filter(issue -> issue.projectId().equals(projectId))
                        .filter(issue -> issue.number().equals(number))
                        .findFirst()
        );
    }

    @Override
    public CompletionStage<List<Issue>> findOpenByProjectId(ProjectId projectId) {
        return CompletableFuture.completedFuture(
                issuesById.values().stream()
                        .filter(issue -> issue.projectId().equals(projectId))
                        .filter(this::isOpen)
                        .toList()
        );
    }

    @Override
    public CompletionStage<List<Issue>> findByRiskId(RiskId riskId) {
        return CompletableFuture.completedFuture(
                issuesById.values().stream()
                        .filter(issue -> riskId.equals(issue.sourceRiskId()))
                        .toList()
        );
    }

    private boolean isOpen(Issue issue) {
        return issue.status() != IssueStatus.CLOSED
                && issue.status() != IssueStatus.REJECTED;
    }

    public Issue get(IssueId id) {
        return issuesById.get(id);
    }
}