package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.spi.port.ProjectRepository;

import java.util.Objects;
import java.util.Optional;

/** Loads a project, plans it and publishes the raised events. */
public final class PlanProjectHandler
        implements CommandHandler<PlanProjectCommand, Result<ProjectId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of("PROJECT_NOT_FOUND", "Project does not exist");

    private final ProjectRepository repository;
    private final EventPublisher eventPublisher;

    public PlanProjectHandler(
            ProjectRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectId>> handle(PlanProjectCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findById(command.projectId()))
                .onItem()
                .transformToUni(maybeProject -> planAndPublish(maybeProject, command));
    }

    private Uni<Result<ProjectId>> planAndPublish(
            Optional<Project> maybeProject,
            PlanProjectCommand command
    ) {
        if (maybeProject.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var project = maybeProject.get();
        project.plan(command.plannedPeriod());

        return Uni.createFrom()
                .completionStage(repository.save(project))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
