package tech.kayys.numou.construction.application.procurement.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.procurement.command.CreateMaterialRequirementCommand;
import tech.kayys.numou.construction.domain.procurement.MaterialRequirement;
import tech.kayys.numou.construction.spi.procurement.MaterialRequirementRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateMaterialRequirementHandler implements CommandHandler<CreateMaterialRequirementCommand, MaterialRequirement> {
    private final MaterialRequirementRepository repository;
    private final EventPublisher eventPublisher;

    public CreateMaterialRequirementHandler(MaterialRequirementRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<MaterialRequirement> handle(CreateMaterialRequirementCommand command) {
        var req = MaterialRequirement.create(command.projectId(), command.siteId());
        return Uni.createFrom()
                .completionStage(repository.save(req))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}
