package tech.kayys.numou.construction.application.workforce.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.workforce.command.CreateLaborRequirementCommand;
import tech.kayys.numou.construction.domain.workforce.LaborRequirement;
import tech.kayys.numou.construction.spi.workforce.LaborRequirementRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateLaborRequirementHandler implements CommandHandler<CreateLaborRequirementCommand, LaborRequirement> {
    private final LaborRequirementRepository repository;
    private final EventPublisher eventPublisher;

    public CreateLaborRequirementHandler(LaborRequirementRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<LaborRequirement> handle(CreateLaborRequirementCommand command) {
        var req = LaborRequirement.create(command.projectId(), command.siteId(), command.trade(), command.workersRequired(), command.requiredDate());
        return Uni.createFrom()
                .completionStage(repository.save(req))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}
