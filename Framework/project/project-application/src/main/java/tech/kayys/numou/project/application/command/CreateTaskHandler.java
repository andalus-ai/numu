package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.Command;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.phase.ProjectPhase;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectStatus;
import tech.kayys.syirkah.project.domain.task.ProjectTask;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.spi.port.ProjectMilestoneRepository;
import tech.kayys.syirkah.project.spi.port.ProjectPhaseRepository;
import tech.kayys.syirkah.project.spi.port.ProjectRepository;
import tech.kayys.syirkah.project.spi.port.ProjectTaskRepository;

import java.util.Objects;
import java.util.Optional;

public final class CreateTaskHandler
        implements CommandHandler<CreateTaskCommand, Result<ProjectTaskId>> {

    public CreateTaskHandler(
            ProjectRepository projects,
            ProjectPhaseRepository phases,
            ProjectMilestoneRepository milestones,
            ProjectTaskRepository tasks,
            EventPublisher eventPublisher
    ) {
        this.projects = Objects.requireNonNull(projects);
        this.phases = Objects.requireNonNull(phases);
        this.milestones = Objects.requireNonNull(milestones);
        this.tasks = Objects.requireNonNull(tasks);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    private static final ApplicationError PROJECT_NOT_FOUND =
            ApplicationError.of("PROJECT_NOT_FOUND", "Project does not exist");

    private static final ApplicationError PROJECT_NOT_OPEN =
            ApplicationError.of(
                    "PROJECT_NOT_ACCEPTING_TASKS",
                    "Completed or cancelled project cannot accept new tasks"
            );

    private static final ApplicationError PHASE_NOT_FOUND =
            ApplicationError.of("PHASE_NOT_FOUND", "Project phase does not exist");

    private static final ApplicationError MILESTONE_NOT_FOUND =
            ApplicationError.of("MILESTONE_NOT_FOUND", "Project milestone does not exist");

    private final ProjectRepository projects;
    private final ProjectPhaseRepository phases;
    private final ProjectMilestoneRepository milestones;
    private final ProjectTaskRepository tasks;
    private final EventPublisher eventPublisher;

    @Override
    public Uni<Result<ProjectTaskId>> handle(CreateTaskCommand command) {
        return Uni.createFrom()
                .completionStage(projects.findById(command.projectId()))
                .onItem()
                .transformToUni(maybeProject -> create(maybeProject, command));
    }

    private static <T> Uni<Result<T>> failure(ApplicationError error) {
        return Uni.createFrom().item(Result.failure(error));
    }

    private Uni<Result<ProjectTaskId>> create(
            Optional<Project> maybeProject,
            CreateTaskCommand command
    ) {
        if (maybeProject.isEmpty()) {
            return failure(PROJECT_NOT_FOUND);
        }

        var project = maybeProject.get();

        if (project.status() == ProjectStatus.COMPLETED
                || project.status() == ProjectStatus.CANCELLED) {
            return failure(PROJECT_NOT_OPEN);
        }

        return verifyPhase(command).flatMap(phaseOk -> {

            if (!phaseOk) {
                return failure(PHASE_NOT_FOUND);
            }

            return verifyMilestone(command).flatMap(milestoneOk -> {

                if (!milestoneOk) {
                    return failure(MILESTONE_NOT_FOUND);
                }

                var task = ProjectTask.create(
                        ProjectTaskId.newId(),
                        project.id(),
                        command.phaseId(),
                        command.milestoneId(),
                        command.parentTaskId(),
                        command.taskNumber(),
                        command.title(),
                        command.description(),
                        command.type(),
                        command.priority(),
                        command.plannedPeriod()
                );

                return Uni.createFrom()
                        .completionStage(tasks.save(task))
                        .onItem()
                        .transformToUni(saved -> eventPublisher
                                .publish(saved.pullDomainEvents())
                                .replaceWith(Result.success(saved.id())));
            });
        });
    }

    private Uni<Boolean> verifyPhase(CreateTaskCommand command) {
        if (command.phaseId() == null) {
            return Uni.createFrom().item(true);
        }

        return Uni.createFrom()
                .completionStage(phases.findById(command.phaseId()))
                .map(maybePhase -> maybePhase
                        .map(phase -> phase.projectId().equals(command.projectId()))
                        .orElse(false));
    }

    private Uni<Boolean> verifyMilestone(CreateTaskCommand command) {
        if (command.milestoneId() == null) {
            return Uni.createFrom().item(true);
        }

        return Uni.createFrom()
                .completionStage(milestones.findById(command.milestoneId()))
                .map(maybeMilestone -> maybeMilestone
                        .map(milestone -> milestone.projectId().equals(command.projectId()))
                        .orElse(false));
    }
}