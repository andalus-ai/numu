package tech.kayys.syirkah.construction.application.quality.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.quality.command.CreateQualityPlanCommand;
import tech.kayys.syirkah.construction.domain.quality.QualityPlan;
import tech.kayys.syirkah.construction.spi.quality.QualityPlanRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateQualityPlanHandler implements CommandHandler<CreateQualityPlanCommand, QualityPlan> {
    private final QualityPlanRepository repository;
    private final EventPublisher eventPublisher;

    public CreateQualityPlanHandler(QualityPlanRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<QualityPlan> handle(CreateQualityPlanCommand command) {
        var plan = QualityPlan.create(command.projectId(), command.title());
        return Uni.createFrom()
                .completionStage(repository.save(plan))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}
