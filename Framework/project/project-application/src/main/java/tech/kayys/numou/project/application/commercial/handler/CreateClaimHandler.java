package tech.kayys.numou.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.project.application.commercial.CommercialErrors;
import tech.kayys.numou.project.application.commercial.command.CreateClaimCommand;
import tech.kayys.numou.project.domain.commercial.ProjectClaim;
import tech.kayys.numou.project.domain.commercial.ProjectClaimId;
import tech.kayys.numou.project.spi.port.ProjectClaimRepository;

import java.util.Objects;

public final class CreateClaimHandler
        implements CommandHandler<CreateClaimCommand, Result<ProjectClaimId>> {

    private final ProjectClaimRepository repository;
    private final EventPublisher eventPublisher;

    public CreateClaimHandler(
            ProjectClaimRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectClaimId>> handle(CreateClaimCommand command) {
        var id = ProjectClaimId.generate();
        var claim = ProjectClaim.create(
                id,
                command.projectId(),
                command.contractId(),
                command.type(),
                command.number(),
                command.title(),
                command.description(),
                command.claimedAmount()
        );

        return Uni.createFrom()
                .completionStage(repository.save(claim))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
