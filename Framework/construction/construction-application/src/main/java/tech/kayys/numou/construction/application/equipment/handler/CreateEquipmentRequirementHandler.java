package tech.kayys.syirkah.construction.application.equipment.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.equipment.command.CreateEquipmentRequirementCommand;
import tech.kayys.syirkah.construction.domain.equipment.EquipmentRequirement;
import tech.kayys.syirkah.construction.spi.equipment.EquipmentRequirementRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateEquipmentRequirementHandler implements CommandHandler<CreateEquipmentRequirementCommand, EquipmentRequirement> {
    private final EquipmentRequirementRepository repository;
    private final EventPublisher eventPublisher;

    public CreateEquipmentRequirementHandler(EquipmentRequirementRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<EquipmentRequirement> handle(CreateEquipmentRequirementCommand command) {
        var req = EquipmentRequirement.create(command.projectId(), command.siteId(), command.equipmentType(), command.quantityRequired(), command.startDate(), command.endDate());
        return Uni.createFrom()
                .completionStage(repository.save(req))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}
