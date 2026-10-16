package tech.kayys.syirkah.construction.application.contract.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.contract.command.CreateConstructionContractCommand;
import tech.kayys.syirkah.construction.domain.contract.ConstructionContract;
import tech.kayys.syirkah.construction.spi.contract.ConstructionContractRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
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
