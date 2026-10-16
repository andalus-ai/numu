package tech.kayys.syirkah.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.commercial.CommercialErrors;
import tech.kayys.syirkah.project.application.commercial.command.AcceptClaimCommand;
import tech.kayys.syirkah.project.domain.commercial.ProjectClaimId;
import tech.kayys.syirkah.project.spi.port.ProjectClaimRepository;

import java.util.Objects;

public final class AcceptClaimHandler
        implements CommandHandler<AcceptClaimCommand, Result<ProjectClaimId>> {

    private final ProjectClaimRepository repository;
    private final EventPublisher eventPublisher;

    public AcceptClaimHandler(
            ProjectClaimRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectClaimId>> handle(AcceptClaimCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findById(command.claimId()))
                .onItem()
                .transformToUni(claimOpt -> {
                    if (claimOpt.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.CLAIM_NOT_FOUND,
                                                "Claim not found: " + command.claimId().value()
                                        )
                                )
                        );
                    }

                    var claim = claimOpt.get();
                    try {
                        claim.accept(command.acceptedAmount());
                    } catch (RuntimeException ex) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.INVALID_COMMERCIAL_STATE,
                                                ex.getMessage()
                                        )
                                )
                        );
                    }

                    return Uni.createFrom()
                            .completionStage(repository.save(claim))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
