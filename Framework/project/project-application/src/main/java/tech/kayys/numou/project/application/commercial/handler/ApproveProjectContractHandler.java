package tech.kayys.numou.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.project.application.commercial.CommercialErrors;
import tech.kayys.numou.project.application.commercial.command.ApproveProjectContractCommand;
import tech.kayys.numou.project.domain.commercial.ProjectContractId;
import tech.kayys.numou.project.spi.port.ProjectContractRepository;

import java.util.Objects;

public final class ApproveProjectContractHandler
        implements CommandHandler<ApproveProjectContractCommand, Result<ProjectContractId>> {

    private final ProjectContractRepository repository;
    private final EventPublisher eventPublisher;

    public ApproveProjectContractHandler(
            ProjectContractRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectContractId>> handle(ApproveProjectContractCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findById(command.contractId()))
                .onItem()
                .transformToUni(contractOpt -> {
                    if (contractOpt.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.CONTRACT_NOT_FOUND,
                                                "Contract not found: " + command.contractId().value()
                                        )
                                )
                        );
                    }

                    var contract = contractOpt.get();
                    try {
                        contract.approve();
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
                            .completionStage(repository.save(contract))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
