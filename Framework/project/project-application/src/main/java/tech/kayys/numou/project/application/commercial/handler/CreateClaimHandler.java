package tech.kayys.syirkah.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.commercial.CommercialErrors;
import tech.kayys.syirkah.project.application.commercial.command.CreateClaimCommand;
import tech.kayys.syirkah.project.domain.commercial.ProjectClaim;
import tech.kayys.syirkah.project.domain.commercial.ProjectClaimId;
import tech.kayys.syirkah.project.spi.port.ProjectClaimRepository;

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
