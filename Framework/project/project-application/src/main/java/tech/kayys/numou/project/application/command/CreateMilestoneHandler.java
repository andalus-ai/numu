package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestone;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectStatus;
import tech.kayys.syirkah.project.spi.port.ProjectMilestoneRepository;
import tech.kayys.syirkah.project.spi.port.ProjectRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Creates a milestone for a project that can still accept one, then
 * publishes the milestone events.
 */
public final class CreateMilestoneHandler
        implements CommandHandler<CreateMilestoneCommand, Result<ProjectMilestoneId>> {

    private static final ApplicationError PROJECT_NOT_FOUND =
            ApplicationError.of("PROJECT_NOT_FOUND", "Project does not exist");

    private static final ApplicationError PROJECT_NOT_OPEN =
            ApplicationError.of(
                    "PROJECT_NOT_ACCEPTING_MILESTONES",
                    "Completed or cancelled project cannot accept new milestones"
            );

    private final ProjectRepository projects;
    private final ProjectMilestoneRepository milestones;
    private final EventPublisher eventPublisher;

    public CreateMilestoneHandler(
            ProjectRepository projects,
            ProjectMilestoneRepository milestones,
            EventPublisher eventPublisher
    ) {
        this.projects = Objects.requireNonNull(projects);
        this.milestones = Objects.requireNonNull(milestones);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectMilestoneId>> handle(CreateMilestoneCommand command) {
        return Uni.createFrom()
                .completionStage(projects.findById(command.projectId()))
                .onItem()
                .transformToUni(maybeProject -> create(maybeProject, command));
    }

    private Uni<Result<ProjectMilestoneId>> create(
            Optional<Project> maybeProject,
            CreateMilestoneCommand command
    ) {
        if (maybeProject.isEmpty()) {
            return Uni.createFrom().item(Result.failure(PROJECT_NOT_FOUND));
        }

        var project = maybeProject.get();

        if (project.status() == ProjectStatus.COMPLETED
                || project.status() == ProjectStatus.CANCELLED) {
            return Uni.createFrom().item(Result.failure(PROJECT_NOT_OPEN));
        }

        var milestone = ProjectMilestone.create(
                ProjectMilestoneId.generate(),
                project.id(),
                command.sequence(),
                command.name(),
                command.type(),
                command.plannedDate()
        );

        return Uni.createFrom()
                .completionStage(milestones.save(milestone))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
