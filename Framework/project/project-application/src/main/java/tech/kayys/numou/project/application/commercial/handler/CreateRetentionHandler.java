package tech.kayys.numou.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.project.application.commercial.CommercialErrors;
import tech.kayys.numou.project.application.commercial.command.CreateRetentionCommand;
import tech.kayys.numou.project.domain.commercial.ProjectRetention;
import tech.kayys.numou.project.domain.commercial.ProjectRetentionId;
import tech.kayys.numou.project.spi.port.ProjectRetentionRepository;

import java.util.Objects;

public final class CreateRetentionHandler
        implements CommandHandler<CreateRetentionCommand, Result<ProjectRetentionId>> {

    private final ProjectRetentionRepository repository;
    private final EventPublisher eventPublisher;

    public CreateRetentionHandler(
            ProjectRetentionRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectRetentionId>> handle(CreateRetentionCommand command) {
        var id = ProjectRetentionId.generate();
        var retention = ProjectRetention.create(
                id,
                command.projectId(),
                command.contractId(),
                command.amount()
        );

        return Uni.createFrom()
                .completionStage(repository.save(retention))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
