package tech.kayys.numou.construction.application.contract.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.contract.command.CreateConstructionContractCommand;
import tech.kayys.numou.construction.domain.contract.ConstructionContract;
import tech.kayys.numou.construction.spi.contract.ConstructionContractRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateConstructionContractHandler implements CommandHandler<CreateConstructionContractCommand, ConstructionContract> {
    private final ConstructionContractRepository repository;
    private final EventPublisher eventPublisher;

    public CreateConstructionContractHandler(ConstructionContractRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<ConstructionContract> handle(CreateConstructionContractCommand command) {
        var contract = ConstructionContract.create(command.projectId(), command.contractNumber(), command.title(), command.client(), command.contractor(), command.originalValue());
        return Uni.createFrom()
                .completionStage(repository.save(contract))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}
