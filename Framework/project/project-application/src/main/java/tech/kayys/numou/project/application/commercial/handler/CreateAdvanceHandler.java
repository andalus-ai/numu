package tech.kayys.syirkah.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.commercial.CommercialErrors;
import tech.kayys.syirkah.project.application.commercial.command.CreateAdvanceCommand;
import tech.kayys.syirkah.project.domain.commercial.ProjectAdvance;
import tech.kayys.syirkah.project.domain.commercial.ProjectAdvanceId;
import tech.kayys.syirkah.project.spi.port.ProjectAdvanceRepository;

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
