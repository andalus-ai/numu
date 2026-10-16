package tech.kayys.syirkah.project.application.commercial.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.project.application.commercial.CommercialErrors;
import tech.kayys.syirkah.project.application.commercial.command.CreateProjectContractCommand;
import tech.kayys.syirkah.project.domain.commercial.ProjectContract;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.spi.port.ProjectContractRepository;

import java.util.Objects;

public final class CreateProjectContractHandler
        implements CommandHandler<CreateProjectContractCommand, Result<ProjectContractId>> {

    private final ProjectContractRepository repository;
    private final EventPublisher eventPublisher;

    public CreateProjectContractHandler(
            ProjectContractRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProjectContractId>> handle(CreateProjectContractCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findByProjectId(command.projectId()))
                .onItem()
                .transformToUni(existingOpt -> {
                    if (existingOpt.isPresent()) {
                        return Uni.createFrom().item(
                                Result.failure(
                                        ApplicationError.of(
                                                CommercialErrors.CONTRACT_ALREADY_EXISTS,
                                                "Project contract already exists for project: " + command.projectId().value()
                                        )
                                )
                        );
                    }

                    var id = ProjectContractId.generate();
                    var contract = ProjectContract.create(
                            id,
                            command.projectId(),
                            command.contractType(),
                            command.contractPeriod(),
                            command.contractValue()
                    );

                    return Uni.createFrom()
                            .completionStage(repository.save(contract))
                            .onItem()
                            .transformToUni(saved -> eventPublisher
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
