package tech.kayys.syirkah.project.application.risk.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.risk.RiskErrors;
import tech.kayys.syirkah.project.application.risk.command.RaiseIssueCommand;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.Issue;
import tech.kayys.syirkah.project.domain.risk.IssueId;
import tech.kayys.syirkah.project.spi.port.IssueRepository;
import tech.kayys.syirkah.project.spi.port.ProjectRepository;
import tech.kayys.syirkah.project.spi.port.RiskRepository;

import java.util.Objects;

/**
 * Raises an issue directly: project must exist, number must be unique
 * per project, and a supplied source risk must exist (and belong to
 * the same project).
 */
public final class RaiseIssueHandler
        implements CommandHandler<RaiseIssueCommand, Result<IssueId>> {

    private static final ApplicationError PROJECT_NOT_FOUND =
            ApplicationError.of(RiskErrors.PROJECT_NOT_FOUND, "Project does not exist");

    private static final ApplicationError RISK_NOT_FOUND =
            ApplicationError.of(RiskErrors.RISK_NOT_FOUND, "Source risk does not exist");

    private static final ApplicationError DUPLICATE_NUMBER =
            ApplicationError.of(
                    RiskErrors.DUPLICATE_ISSUE_NUMBER,
                    "Issue number already exists in this project"
            );

    private final ProjectRepository projects;
    private final RiskRepository risks;
    private final IssueRepository issues;
    private final EventPublisher eventPublisher;

    public RaiseIssueHandler(
            ProjectRepository projects,
            RiskRepository risks,
            IssueRepository issues,
            EventPublisher eventPublisher
    ) {
        this.projects = Objects.requireNonNull(projects);
        this.risks = Objects.requireNonNull(risks);
        this.issues = Objects.requireNonNull(issues);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<IssueId>> handle(RaiseIssueCommand command) {
        return Uni.createFrom()
                .completionStage(projects.findById(command.projectId()))
                .onItem()
                .transformToUni(maybeProject -> {

                    if (maybeProject.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(PROJECT_NOT_FOUND));
                    }

                    var sourceRisk = command.sourceRiskId();
                    if (sourceRisk == null) {
                        return checkNumberAndRaise(command);
                    }

                    return Uni.createFrom()
                            .completionStage(risks.findById(sourceRisk))
                            .onItem()
                            .transformToUni(maybeRisk -> {

                                if (maybeRisk.isEmpty()
                                        || !maybeRisk.get().projectId().equals(command.projectId())) {
                                    return Uni.createFrom().item(Result.failure(RISK_NOT_FOUND));
                                }

                                return checkNumberAndRaise(command);
                            });
                });
    }

    private Uni<Result<IssueId>> checkNumberAndRaise(RaiseIssueCommand command) {
        return Uni.createFrom()
                .completionStage(issues.findByNumber(
                        command.projectId(), command.number()))
                .onItem()
                .transformToUni(maybeExisting -> {

                    if (maybeExisting.isPresent()) {
                        return Uni.createFrom().item(Result.failure(DUPLICATE_NUMBER));
                    }

                    var issue = Issue.raise(
                            IssueId.generate(),
                            command.projectId(),
                            command.sourceRiskId(),
                            command.number(),
                            command.title(),
                            command.description(),
                            command.severity(),
                            command.priority(),
                            command.ownerId()
                    );

                    return Uni.createFrom()
                            .completionStage(issues.save(issue))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
