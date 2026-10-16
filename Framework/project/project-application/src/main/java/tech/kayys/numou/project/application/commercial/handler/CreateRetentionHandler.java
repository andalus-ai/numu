package tech.kayys.syirkah.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.commercial.CommercialErrors;
import tech.kayys.syirkah.project.application.commercial.command.CreateRetentionCommand;
import tech.kayys.syirkah.project.domain.commercial.ProjectRetention;
import tech.kayys.syirkah.project.domain.commercial.ProjectRetentionId;
import tech.kayys.syirkah.project.spi.port.ProjectRetentionRepository;

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
