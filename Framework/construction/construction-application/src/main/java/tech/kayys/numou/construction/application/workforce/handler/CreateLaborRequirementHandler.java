package tech.kayys.syirkah.construction.application.workforce.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.workforce.command.CreateLaborRequirementCommand;
import tech.kayys.syirkah.construction.domain.workforce.LaborRequirement;
import tech.kayys.syirkah.construction.spi.workforce.LaborRequirementRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
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
