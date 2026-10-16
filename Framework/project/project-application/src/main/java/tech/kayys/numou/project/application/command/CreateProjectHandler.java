package tech.kayys.syirkah.project.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectNumber;
import tech.kayys.syirkah.project.spi.port.ProjectRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Registers a new Project aggregate, persists it and publishes the
 * ProjectCreated event the aggregate raised.
 *
 * The domain stays plain synchronous Java; this handler is where the
 * reactive orchestration lives.
 *
 * Customer existence is validated through CustomerReferencePort at the
 * boundary of the use case (see project01.md section 24) - the Project
 * aggregate itself only ever holds the customer identifier.
 */
public final class CreateProjectHandler
        implements CommandHandler<CreateProjectCommand, Result<ProjectId>> {

    private final ProjectRepository repository;
    private final EventPublisher eventPublisher;

    public CreateProjectHandler(
            ProjectRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectId>> handle(CreateProjectCommand command) {
        var number = ProjectNumber.of(command.projectNumber());

        return Uni.createFrom()
                .completionStage(repository.findByNumber(number))
                .onItem()
                .transformToUni(existing -> createIfNumberIsFree(existing, number, command));
    }

    private Uni<Result<ProjectId>> createIfNumberIsFree(
            Optional<Project> existing,
            ProjectNumber number,
            CreateProjectCommand command
    ) {
        if (existing.isPresent()) {
            return Uni.createFrom().item(
                    Result.failure(
                            ApplicationError.of(
                                    "PROJECT_NUMBER_ALREADY_USED",
                                    "Project number is already used: " + number.value()
                            )
                    )
            );
        }

        var project = Project.create(
                ProjectId.generate(),
                number,
                command.name(),
                command.type(),
                command.customerId()
        );

        return Uni.createFrom()
                .completionStage(repository.save(project))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
