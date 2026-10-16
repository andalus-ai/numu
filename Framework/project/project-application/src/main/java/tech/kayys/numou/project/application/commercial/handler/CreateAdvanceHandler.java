package tech.kayys.numou.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.project.application.commercial.CommercialErrors;
import tech.kayys.numou.project.application.commercial.command.CreateAdvanceCommand;
import tech.kayys.numou.project.domain.commercial.ProjectAdvance;
import tech.kayys.numou.project.domain.commercial.ProjectAdvanceId;
import tech.kayys.numou.project.spi.port.ProjectAdvanceRepository;

import java.util.Objects;

public final class CreateAdvanceHandler
        implements CommandHandler<CreateAdvanceCommand, Result<ProjectAdvanceId>> {

    private final ProjectAdvanceRepository repository;
    private final EventPublisher eventPublisher;

    public CreateAdvanceHandler(
            ProjectAdvanceRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectAdvanceId>> handle(CreateAdvanceCommand command) {
        var id = ProjectAdvanceId.generate();
        var advance = ProjectAdvance.create(
                id,
                command.projectId(),
                command.contractId(),
                command.amount()
        );

        return Uni.createFrom()
                .completionStage(repository.save(advance))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
