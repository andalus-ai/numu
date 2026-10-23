package tech.kayys.numou.planning.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import tech.kayys.numou.planning.application.port.PlanRepository;
import tech.kayys.numou.planning.application.port.ScheduleChangeExecutionStore;
import tech.kayys.numou.planning.domain.ScheduleChangeExecution;
import tech.kayys.numou.planning.domain.ScheduleChangeRequest;
import tech.kayys.numou.planning.domain.SchedulePlan;

/**
 * Application use case for applying a fully materialized target plan after schedule-change approval.
 * Atomic revision compare-and-set and durable idempotency are delegated to the execution store.
 */
public final class ApplyApprovedScheduleChange {
    private final PlanRepository plans;
    private final ScheduleChangeExecutionStore executions;
    private final Clock clock;

    public ApplyApprovedScheduleChange(PlanRepository plans,
            ScheduleChangeExecutionStore executions, Clock clock) {
        this.plans = Objects.requireNonNull(plans, "Plan repository cannot be null");
        this.executions = Objects.requireNonNull(executions, "Execution store cannot be null");
        this.clock = Objects.requireNonNull(clock, "Clock cannot be null");
    }

    public ScheduleChangeExecution execute(UUID executionId, ScheduleChangeRequest request,
            UUID initiatedBy, SchedulePlan targetPlan) {
        Objects.requireNonNull(executionId, "Execution id cannot be null");
        Objects.requireNonNull(request, "Change request cannot be null");
        Objects.requireNonNull(initiatedBy, "Initiator cannot be null");
        Objects.requireNonNull(targetPlan, "Target plan cannot be null");

        validateTarget(request, targetPlan);

        // Check durable idempotency before reading the current plan: a retry must return the
        // original receipt even if the plan has advanced since the first successful attempt.
        var prior = executions.findByExecutionId(executionId);
        if (prior.isPresent()) {
            ScheduleChangeExecution existing = prior.get();
            if (!existing.requestId().equals(request.requestId())
                    || !existing.tenantId().equals(request.tenantId())
                    || !existing.structureId().equals(request.structureId())
                    || !existing.planId().equals(request.planId())
                    || existing.baseRevision() != request.baseRevision()
                    || existing.targetRevision() != request.targetRevision()
                    || !executions.matchesReplay(executionId, request, targetPlan, initiatedBy)) {
                throw new IllegalArgumentException("Execution id was already used for a different change payload");
            }
            return existing;
        }

        var current = plans.findById(request.planId())
                .orElseThrow(() -> new IllegalArgumentException("Schedule plan was not found"));
        ScheduleChangeExecution applying = ScheduleChangeExecution.begin(
                executionId, request, initiatedBy, clock.instant(), current.revision());

        ScheduleChangeExecution result = Objects.requireNonNull(
                executions.applyAtomically(request, targetPlan, applying),
                "Execution store must return the durable execution receipt");
        if (!result.executionId().equals(executionId)
                || !result.requestId().equals(request.requestId())
                || !result.planId().equals(request.planId())) {
            throw new IllegalStateException("Execution store returned a receipt for a different operation");
        }
        if (result.status() == ScheduleChangeExecution.Status.APPLYING) {
            throw new IllegalStateException("Atomic execution store must return a terminal receipt");
        }
        return result;
    }

    private static void validateTarget(ScheduleChangeRequest request, SchedulePlan targetPlan) {
        if (request.status() != ScheduleChangeRequest.Status.APPROVED) {
            throw new IllegalStateException("Only an approved schedule change can be applied");
        }
        if (!request.planId().equals(targetPlan.id())) {
            throw new IllegalArgumentException("Target plan identity does not match the approved request");
        }
        if (targetPlan.revision() != request.targetRevision()) {
            throw new IllegalArgumentException("Target plan must be materialized at the approved target revision");
        }
    }
}
