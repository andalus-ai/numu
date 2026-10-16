package tech.kayys.numou.asset.application.pm;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.maintenance.due.MaintenanceDue;
import tech.kayys.numou.asset.domain.maintenance.due.MaintenanceDueCalculator;
import tech.kayys.numou.asset.domain.maintenance.due.MaintenanceDueCalculator.DuePoint;
import tech.kayys.numou.asset.domain.maintenance.due.MaintenanceDueCalculator.EvaluationContext;
import tech.kayys.numou.asset.domain.maintenance.due.MaintenanceDueId;
import tech.kayys.numou.asset.domain.maintenance.due.MaintenanceDueStatus;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenancePlan;
import tech.kayys.numou.asset.domain.maintenance.plan.MaintenanceRule;
import tech.kayys.numou.asset.domain.maintenance.schedule.MaintenanceSchedule;
import tech.kayys.numou.asset.domain.maintenance.schedule.MaintenanceScheduleId;
import tech.kayys.numou.asset.domain.repository.MaintenanceDueRepository;
import tech.kayys.numou.asset.domain.repository.MaintenancePlanRepository;
import tech.kayys.numou.asset.domain.repository.MaintenanceScheduleRepository;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evaluates dues for one schedule and persists new occurrences idempotently:
 * an equal open due (same schedule + rule + due point) is never duplicated.
 */
public class EvaluateDueHandler extends AbstractPmCommandHandler
        implements CommandHandler<EvaluateDueHandler.EvaluateDueCommand, Result<List<String>>> {

    public record EvaluateDueCommand(
            String tenantId, String scheduleId, BigDecimal currentMeterValue) implements Command {}

    private final MaintenanceScheduleRepository schedules;
    private final MaintenancePlanRepository plans;
    private final MaintenanceDueRepository dues;

    public EvaluateDueHandler(MaintenanceScheduleRepository schedules, MaintenancePlanRepository plans,
                              MaintenanceDueRepository dues, EventPublisher eventPublisher,
                              UnitOfWork unitOfWork, DomainClock clock) {
        super(eventPublisher, unitOfWork, clock);
        this.schedules = Objects.requireNonNull(schedules, "schedules");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.dues = Objects.requireNonNull(dues, "dues");
    }

    @Override
    public Uni<Result<List<String>>> handle(EvaluateDueCommand command) {
        MaintenanceScheduleId scheduleId = MaintenanceScheduleId.of(UUID.fromString(command.scheduleId()));
        return Uni.createFrom().completionStage(() -> schedules.findByTenantAndId(command.tenantId(), scheduleId))
                .flatMap(scheduleOpt -> {
                    if (scheduleOpt.isEmpty()) {
                        return Uni.createFrom().item(Result.<List<String>>failure(ApplicationError.of(
                                "maintenance.schedule.not-found", "Schedule not found: " + command.scheduleId())));
                    }
                    MaintenanceSchedule schedule = scheduleOpt.get();
                    return Uni.createFrom()
                            .completionStage(() -> plans.findByTenantAndId(command.tenantId(), schedule.planId()))
                            .flatMap(planOpt -> {
                                if (planOpt.isEmpty()) {
                                    return Uni.createFrom().item(Result.<List<String>>failure(ApplicationError.of(
                                            "maintenance.plan.not-found", "Plan not found")));
                                }
                                return evaluateAll(command, schedule, planOpt.get());
                            });
                });
    }

    private Uni<Result<List<String>>> evaluateAll(EvaluateDueCommand command, MaintenanceSchedule schedule,
                                                  MaintenancePlan plan) {
        EvaluationContext ctx = new EvaluationContext(clock.now(), command.currentMeterValue(), clock.now());
        List<Uni<Result<String>>> perRule = new ArrayList<>();
        for (MaintenanceRule rule : plan.activeRules()) {
            perRule.add(evaluateRule(command.tenantId(), schedule, plan, rule, ctx));
        }
        Uni<Result<List<String>>> acc = Uni.createFrom().item(Result.success(new ArrayList<>()));
        for (Uni<Result<String>> step : perRule) {
            acc = acc.flatMap(collected -> {
                if (collected.isFailure()) {
                    return Uni.createFrom().item(collected);
                }
                return step.map(single -> {
                    if (single.isFailure()) {
                        return Result.failure(((Result.Failure<String>) single).error());
                    }
                    List<String> next = new ArrayList<>(collected.orElseThrow());
                    String id = single.orElseThrow();
                    if (id != null) {
                        next.add(id);
                    }
                    return Result.success(List.copyOf(next));
                });
            });
        }
        return acc;
    }

    private Uni<Result<String>> evaluateRule(String tenantId, MaintenanceSchedule schedule,
                                             MaintenancePlan plan, MaintenanceRule rule,
                                             EvaluationContext ctx) {
        DuePoint point = MaintenanceDueCalculator.calculate(schedule, rule, ctx);
        MaintenanceDueStatus status = point.status() == MaintenanceDueStatus.OVERDUE ? MaintenanceDueStatus.OVERDUE
                : point.status() == MaintenanceDueStatus.DUE ? MaintenanceDueStatus.DUE
                : MaintenanceDueStatus.UPCOMING;
        return Uni.createFrom()
                .completionStage(() -> dues.findOpenByScheduleAndRule(tenantId, schedule.id().value(), rule.id()))
                .flatMap(open -> {
                    MaintenanceDue probe = MaintenanceDue.reconstitute(MaintenanceDueId.generate(), tenantId,
                            schedule.id(), schedule.assetId(), plan.id(), rule.id(), 1, rule.type(),
                            point.dueAt(), point.dueMeterValue(), status, null, null);
                    for (MaintenanceDue existing : open) {
                        if (existing.sameOpenDue(probe)) {
                            return Uni.createFrom().item(Result.<String>success(null));
                        }
                    }
                    int occurrence = open.size() + 1;
                    MaintenanceDue due = MaintenanceDue.create(MaintenanceDueId.generate(), tenantId,
                            schedule.id(), schedule.assetId(), plan.id(), rule.id(), occurrence, rule.type(),
                            point.dueAt(), point.dueMeterValue(), status, clock);
                    return saveAndPublish(dues.save(due), due)
                            .map(saved -> Result.success(saved.id().value().toString()));
                });
    }
}
