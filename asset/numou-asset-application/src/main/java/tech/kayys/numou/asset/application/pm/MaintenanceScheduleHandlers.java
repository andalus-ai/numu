package tech.kayys.numou.asset.application.pm;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.identifier.AssetId;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenancePlanId;
import tech.kayys.numou.asset.domain.maintenance.schedule.MaintenanceSchedule;
import tech.kayys.numou.asset.domain.maintenance.schedule.MaintenanceScheduleId;
import tech.kayys.numou.asset.domain.maintenance.schedule.MeterScheduleBaseline;
import tech.kayys.numou.asset.domain.repository.AssetRepository;
import tech.kayys.numou.asset.domain.repository.MaintenancePlanRepository;
import tech.kayys.numou.asset.domain.repository.MaintenanceScheduleRepository;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Schedule commands: validates plan + asset refs belong to the same tenant (ASSET-22). */
public final class MaintenanceScheduleHandlers {
    private MaintenanceScheduleHandlers() {}

    public record CreateMaintenanceScheduleCommand(
            String tenantId, String assetId, String planId, Instant effectiveFrom,
            Instant lastServiceAt, List<MeterScheduleBaseline> baselines) implements Command {}

    public static final class Create extends AbstractPmCommandHandler
            implements CommandHandler<CreateMaintenanceScheduleCommand, Result<String>> {
        private final MaintenanceScheduleRepository schedules;
        private final MaintenancePlanRepository plans;
        private final AssetRepository assets;

        public Create(MaintenanceScheduleRepository schedules, MaintenancePlanRepository plans,
                      AssetRepository assets, EventPublisher eventPublisher,
                      UnitOfWork unitOfWork, DomainClock clock) {
            super(eventPublisher, unitOfWork, clock);
            this.schedules = Objects.requireNonNull(schedules, "schedules");
            this.plans = Objects.requireNonNull(plans, "plans");
            this.assets = Objects.requireNonNull(assets, "assets");
        }

        @Override
        public Uni<Result<String>> handle(CreateMaintenanceScheduleCommand command) {
            MaintenancePlanId planId = MaintenancePlanId.of(UUID.fromString(command.planId()));
            AssetId assetId = AssetId.of(UUID.fromString(command.assetId()));
            return Uni.createFrom().completionStage(() -> plans.findByTenantAndId(command.tenantId(), planId))
                    .flatMap(planOpt -> {
                        if (planOpt.isEmpty()) {
                            return Uni.createFrom().item(Result.<String>failure(ApplicationError.of(
                                    "maintenance.plan.not-found", "Plan not found: " + command.planId())));
                        }
                        return Uni.createFrom().completionStage(
                                        () -> assets.findByTenantAndId(command.tenantId(), assetId))
                                .flatMap(assetOpt -> {
                                    if (assetOpt.isEmpty()) {
                                        return Uni.createFrom().item(Result.<String>failure(ApplicationError.of(
                                                "asset.not-found", "Asset not found: " + command.assetId())));
                                    }
                                    MaintenanceSchedule schedule = MaintenanceSchedule.create(
                                            MaintenanceScheduleId.generate(), command.tenantId(),
                                            assetId.value(), planId, command.effectiveFrom(),
                                            command.lastServiceAt(), command.baselines(), clock);
                                    return saveAndPublish(schedules.save(schedule), schedule)
                                            .map(saved -> Result.success(saved.id().value().toString()));
                                });
                    });
        }
    }

    public record SuspendMaintenanceScheduleCommand(String tenantId, String scheduleId) implements Command {}
    public record ResumeMaintenanceScheduleCommand(String tenantId, String scheduleId) implements Command {}
    public record CancelMaintenanceScheduleCommand(String tenantId, String scheduleId) implements Command {}

    abstract static class Lifecycle<C extends Command> extends AbstractPmCommandHandler {
        protected final MaintenanceScheduleRepository schedules;
        protected final java.util.function.BiConsumer<MaintenanceSchedule, DomainClock> transition;

        Lifecycle(MaintenanceScheduleRepository schedules, EventPublisher eventPublisher,
                  UnitOfWork unitOfWork, DomainClock clock,
                  java.util.function.BiConsumer<MaintenanceSchedule, DomainClock> transition) {
            super(eventPublisher, unitOfWork, clock);
            this.schedules = Objects.requireNonNull(schedules, "schedules");
            this.transition = Objects.requireNonNull(transition, "transition");
        }

        protected Uni<Result<String>> apply(String tenantId, String scheduleId) {
            MaintenanceScheduleId id = MaintenanceScheduleId.of(UUID.fromString(scheduleId));
            return Uni.createFrom().completionStage(() -> schedules.findByTenantAndId(tenantId, id))
                    .flatMap(opt -> {
                        MaintenanceSchedule schedule = opt.orElseThrow(() -> new ApplicationErrorException(
                                ApplicationError.of("maintenance.schedule.not-found", "Schedule not found: " + scheduleId)));
                        transition.accept(schedule, clock);
                        return saveAndPublish(schedules.save(schedule), schedule)
                                .map(saved -> Result.success(saved.id().value().toString()));
                    });
        }
    }

    public static final class Suspend extends Lifecycle<SuspendMaintenanceScheduleCommand>
            implements CommandHandler<SuspendMaintenanceScheduleCommand, Result<String>> {
        public Suspend(MaintenanceScheduleRepository schedules, EventPublisher eventPublisher,
                       UnitOfWork unitOfWork, DomainClock clock) {
            super(schedules, eventPublisher, unitOfWork, clock, MaintenanceSchedule::suspend);
        }
        @Override public Uni<Result<String>> handle(SuspendMaintenanceScheduleCommand command) {
            return apply(command.tenantId(), command.scheduleId());
        }
    }

    public static final class Resume extends Lifecycle<ResumeMaintenanceScheduleCommand>
            implements CommandHandler<ResumeMaintenanceScheduleCommand, Result<String>> {
        public Resume(MaintenanceScheduleRepository schedules, EventPublisher eventPublisher,
                      UnitOfWork unitOfWork, DomainClock clock) {
            super(schedules, eventPublisher, unitOfWork, clock, MaintenanceSchedule::resume);
        }
        @Override public Uni<Result<String>> handle(ResumeMaintenanceScheduleCommand command) {
            return apply(command.tenantId(), command.scheduleId());
        }
    }

    public static final class Cancel extends Lifecycle<CancelMaintenanceScheduleCommand>
            implements CommandHandler<CancelMaintenanceScheduleCommand, Result<String>> {
        public Cancel(MaintenanceScheduleRepository schedules, EventPublisher eventPublisher,
                      UnitOfWork unitOfWork, DomainClock clock) {
            super(schedules, eventPublisher, unitOfWork, clock, MaintenanceSchedule::cancel);
        }
        @Override public Uni<Result<String>> handle(CancelMaintenanceScheduleCommand command) {
            return apply(command.tenantId(), command.scheduleId());
        }
    }
}
