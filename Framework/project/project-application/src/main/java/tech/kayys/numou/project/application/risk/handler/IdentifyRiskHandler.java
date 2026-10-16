package tech.kayys.syirkah.project.application.risk.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.risk.RiskErrors;
import tech.kayys.syirkah.project.application.risk.command.IdentifyRiskCommand;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.Risk;
import tech.kayys.syirkah.project.domain.risk.RiskId;
import tech.kayys.syirkah.project.spi.port.ProjectRepository;
import tech.kayys.syirkah.project.spi.port.RiskRepository;

import java.util.Objects;

/**
 * Identifies a risk: the project must exist (the payload id is a hint,
 * never a fact) and the number must be unique per project.
 *
 * Closure status of the project is deliberately not checked — risks
 * are still identified during closeout (warranty, handover, litigation
 * exposure).
 */
public final class IdentifyRiskHandler
        implements CommandHandler<IdentifyRiskCommand, Result<RiskId>> {

    private static final ApplicationError PROJECT_NOT_FOUND =
            ApplicationError.of(
                    RiskErrors.PROJECT_NOT_FOUND,
                    "Project does not exist"
            );

    private static final ApplicationError DUPLICATE_NUMBER =
            ApplicationError.of(
                    RiskErrors.DUPLICATE_RISK_NUMBER,
                    "Risk number already exists in this project"
            );

    private final ProjectRepository projects;
    private final RiskRepository risks;
    private final EventPublisher eventPublisher;

    public IdentifyRiskHandler(
            ProjectRepository projects,
            RiskRepository risks,
            EventPublisher eventPublisher
    ) {
        this.projects = Objects.requireNonNull(projects);
        this.risks = Objects.requireNonNull(risks);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<RiskId>> handle(IdentifyRiskCommand command) {
        return Uni.createFrom()
                .completionStage(projects.findById(command.projectId()))
                .onItem()
                .transformToUni(maybeProject -> {

                    if (maybeProject.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(PROJECT_NOT_FOUND));
                    }

                    return Uni.createFrom()
                            .completionStage(risks.findByNumber(
                                    command.projectId(), command.number()))
                            .onItem()
                            .transformToUni(maybeExisting -> {

                                if (maybeExisting.isPresent()) {
                                    return Uni.createFrom().item(
                                            Result.failure(DUPLICATE_NUMBER));
                                }

                                var risk = Risk.identify(
                                        RiskId.generate(),
                                        command.projectId(),
                                        command.number(),
                                        command.title(),
                                        command.description(),
                                        command.category(),
                                        command.source(),
                                        command.identifiedDate(),
                                        command.targetDate()
                                );

                                return Uni.createFrom()
                                        .completionStage(risks.save(risk))
                                        .onItem()
                                        .transformToUni(saved -> eventPublisher
                                                .publish(saved.pullDomainEvents())
                                                .replaceWith(Result.success(saved.id())));
                            });
                });
    }
}
