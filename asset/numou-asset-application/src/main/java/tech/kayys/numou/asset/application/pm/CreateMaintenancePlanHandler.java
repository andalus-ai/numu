package tech.kayys.numou.asset.application.pm;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.application.pm.MaintenancePlanCommands.CreateMaintenancePlanCommand;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenancePlan;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenancePlanId;
import tech.kayys.numou.asset.domain.repository.MaintenancePlanRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.Objects;

public class CreateMaintenancePlanHandler extends AbstractPmCommandHandler
        implements CommandHandler<CreateMaintenancePlanCommand, Result<String>> {

    private final MaintenancePlanRepository plans;

    public CreateMaintenancePlanHandler(MaintenancePlanRepository plans, EventPublisher eventPublisher,
                                        UnitOfWork unitOfWork, DomainClock clock) {
        super(eventPublisher, unitOfWork, clock);
        this.plans = Objects.requireNonNull(plans, "plans");
    }

    @Override
    public Uni<Result<String>> handle(CreateMaintenancePlanCommand command) {
        return Uni.createFrom()
                .completionStage(() -> plans.existsByTenantAndPlanNumber(command.tenantId(), command.planNumber()))
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Uni.createFrom().item(Result.<String>failure(ApplicationError.of(
                                "maintenance.plan.duplicate", "Plan number already exists: " + command.planNumber())));
                    }
                    MaintenancePlan plan = MaintenancePlan.create(MaintenancePlanId.generate(),
                            command.tenantId(), command.planNumber(), command.name(),
                            command.description(), command.rules(), clock);
                    return saveAndPublish(plans.save(plan), plan)
                            .map(saved -> Result.success(saved.id().value().toString()));
                });
    }
}
