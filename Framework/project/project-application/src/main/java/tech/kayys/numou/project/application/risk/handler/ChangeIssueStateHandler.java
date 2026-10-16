package tech.kayys.syirkah.project.application.risk.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.risk.RiskErrors;
import tech.kayys.syirkah.project.application.risk.command.CloseIssueCommand;
import tech.kayys.syirkah.project.application.risk.command.InvestigateIssueCommand;
import tech.kayys.syirkah.project.application.risk.command.PlanIssueActionCommand;
import tech.kayys.syirkah.project.application.risk.command.RejectIssueCommand;
import tech.kayys.syirkah.project.application.risk.command.ResolveIssueCommand;
import tech.kayys.syirkah.project.application.risk.command.StartIssueWorkCommand;
import tech.kayys.syirkah.project.domain.risk.Issue;
import tech.kayys.syirkah.project.domain.risk.IssueId;
import tech.kayys.syirkah.project.spi.port.IssueRepository;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Applies one issue transition and publishes issue events.
 * Illegal transitions are rejected by the aggregate
 * (InvalidIssueStateException).
 */
public final class ChangeIssueStateHandler {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(RiskErrors.ISSUE_NOT_FOUND, "Issue does not exist");

    private final IssueRepository issues;
    private final EventPublisher eventPublisher;

    public ChangeIssueStateHandler(
            IssueRepository issues,
            EventPublisher eventPublisher
    ) {
        this.issues = Objects.requireNonNull(issues);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    public Uni<Result<IssueId>> investigate(InvestigateIssueCommand command) {
        return transition(command.issueId(), Issue::investigate);
    }

    public Uni<Result<IssueId>> planAction(PlanIssueActionCommand command) {
        return transition(command.issueId(), Issue::planAction);
    }

    public Uni<Result<IssueId>> startWork(StartIssueWorkCommand command) {
        return transition(command.issueId(), Issue::startWork);
    }

    public Uni<Result<IssueId>> resolve(ResolveIssueCommand command) {
        return transition(command.issueId(),
                issue -> issue.resolve(command.rootCause(), command.resolution()));
    }

    public Uni<Result<IssueId>> close(CloseIssueCommand command) {
        return transition(command.issueId(), Issue::close);
    }

    public Uni<Result<IssueId>> reject(RejectIssueCommand command) {
        return transition(command.issueId(), Issue::reject);
    }

    private Uni<Result<IssueId>> transition(
            IssueId issueId,
            Consumer<Issue> transition
    ) {
        return Uni.createFrom()
                .completionStage(issues.findById(issueId))
                .onItem()
                .transformToUni(maybeIssue -> {

                    if (maybeIssue.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }

                    var issue = maybeIssue.get();
                    transition.accept(issue);

                    return Uni.createFrom()
                            .completionStage(issues.save(issue))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
