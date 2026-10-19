package tech.kayys.numou.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.project.domain.phase.ProjectPhase;
import tech.kayys.numou.project.domain.phase.ProjectPhaseId;
import tech.kayys.numou.project.domain.project.Project;
import tech.kayys.numou.project.domain.project.ProjectStatus;
import tech.kayys.numou.project.spi.port.ProjectPhaseRepository;
import tech.kayys.numou.project.spi.port.ProjectRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Creates a phase for a project that can still accept one, then
 * publishes the phase events.
 */
public final class CreatePhaseHandler
        implements CommandHandler<CreatePhaseCommand, Result<ProjectPhaseId>> {

    private static final ApplicationError PROJECT_NOT_FOUND =
            ApplicationError.of("PROJECT_NOT_FOUND", "Project does not exist");

    private static final ApplicationError PROJECT_NOT_OPEN =
            ApplicationError.of(
                    "PROJECT_NOT_ACCEPTING_PHASES",
                    "Completed or cancelled project cannot accept new phases"
            );

    private final ProjectRepository projects;
    private final ProjectPhaseRepository phases;
    private final EventPublisher eventPublisher;

    public CreatePhaseHandler(
            ProjectRepository projects,
            ProjectPhaseRepository phases,
            EventPublisher eventPublisher
    ) {
        this.projects = Objects.requireNonNull(projects);
        this.phases = Objects.requireNonNull(phases);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectPhaseId>> handle(CreatePhaseCommand command) {
        return Uni.createFrom()
                .completionStage(projects.findById(command.projectId()))
                .onItem()
                .transformToUni(maybeProject -> create(maybeProject, command));
    }

    private Uni<Result<ProjectPhaseId>> create(
            Optional<Project> maybeProject,
            CreatePhaseCommand command
    ) {
        if (maybeProject.isEmpty()) {
            return Uni.createFrom().item(Result.failure(PROJECT_NOT_FOUND));
        }

        var project = maybeProject.get();

        if (project.status() == ProjectStatus.COMPLETED
                || project.status() == ProjectStatus.CANCELLED) {
            return Uni.createFrom().item(Result.failure(PROJECT_NOT_OPEN));
        }

        var phase = ProjectPhase.create(
                ProjectPhaseId.generate(),
                project.id(),
                command.sequence(),
                command.name(),
                command.type()
        );

        return Uni.createFrom()
                .completionStage(phases.save(phase))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
