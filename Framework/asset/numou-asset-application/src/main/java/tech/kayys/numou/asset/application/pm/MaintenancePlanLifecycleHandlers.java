package tech.kayys.numou.asset.application.pm;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.application.pm.MaintenancePlanCommands.ActivateMaintenancePlanCommand;
import tech.kayys.numou.asset.application.pm.MaintenancePlanCommands.RetireMaintenancePlanCommand;
import tech.kayys.numou.asset.application.pm.MaintenancePlanCommands.SuspendMaintenancePlanCommand;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenancePlan;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenancePlanId;
import tech.kayys.numou.asset.domain.repository.MaintenancePlanRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Activate / suspend / retire handlers for maintenance plans (ASSET-22). */
public final class MaintenancePlanLifecycleHandlers {
    private MaintenancePlanLifecycleHandlers() {}

    abstract static class Base<C> extends AbstractPmCommandHandler {
        protected final MaintenancePlanRepository plans;
        protected final BiConsumer<MaintenancePlan, DomainClock> transition;

        Base(MaintenancePlanRepository plans, EventPublisher eventPublisher,
             UnitOfWork unitOfWork, DomainClock clock, BiConsumer<MaintenancePlan, DomainClock> transition) {
            super(eventPublisher, unitOfWork, clock);
            this.plans = Objects.requireNonNull(plans, "plans");
            this.transition = Objects.requireNonNull(transition, "transition");
        }

        protected Uni<Result<String>> apply(String tenantId, String planId) {
            MaintenancePlanId id = MaintenancePlanId.of(UUID.fromString(planId));
            return Uni.createFrom().completionStage(() -> plans.findByTenantAndId(tenantId, id))
                    .flatMap(opt -> {
                        MaintenancePlan plan = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("maintenance.plan.not-found", "Plan not found: " + planId)));
                        transition.accept(plan, clock);
                        return saveAndPublish(plans.save(plan), plan)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public static final class Activate extends Base<ActivateMaintenancePlanCommand>
            implements CommandHandler<ActivateMaintenancePlanCommand, Result<String>> {
        public Activate(MaintenancePlanRepository plans, EventPublisher eventPublisher,
                        UnitOfWork unitOfWork, DomainClock clock) {
            super(plans, eventPublisher, unitOfWork, clock, MaintenancePlan::activate);
        }
        @Override public Uni<Result<String>> handle(ActivateMaintenancePlanCommand command) {
            return apply(command.tenantId(), command.planId());
        }
    }

    public static final class Suspend extends Base<SuspendMaintenancePlanCommand>
            implements CommandHandler<SuspendMaintenancePlanCommand, Result<String>> {
        public Suspend(MaintenancePlanRepository plans, EventPublisher eventPublisher,
                       UnitOfWork unitOfWork, DomainClock clock) {
            super(plans, eventPublisher, unitOfWork, clock, MaintenancePlan::suspend);
        }
        @Override public Uni<Result<String>> handle(SuspendMaintenancePlanCommand command) {
            return apply(command.tenantId(), command.planId());
        }
    }

    public static final class Retire extends Base<RetireMaintenancePlanCommand>
            implements CommandHandler<RetireMaintenancePlanCommand, Result<String>> {
        public Retire(MaintenancePlanRepository plans, EventPublisher eventPublisher,
                      UnitOfWork unitOfWork, DomainClock clock) {
            super(plans, eventPublisher, unitOfWork, clock, MaintenancePlan::retire);
        }
        @Override public Uni<Result<String>> handle(RetireMaintenancePlanCommand command) {
            return apply(command.tenantId(), command.planId());
        }
    }
}
