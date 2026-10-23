package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable execution receipt for an approved schedule change.
 *
 * This aggregate guards domain-level preconditions and reconciliation. The application adapter
 * must still atomically compare-and-set the plan revision and persist the plan plus receipt.
 */
public record ScheduleChangeExecution(
        UUID executionId,
        UUID requestId,
        UUID tenantId,
        UUID structureId,
        PlanId planId,
        long baseRevision,
        long targetRevision,
        UUID initiatedBy,
        Instant startedAt,
        Status status,
        Long resultingRevision,
        Instant completedAt,
        String failureReason) {

    public ScheduleChangeExecution {
        Objects.requireNonNull(executionId, "Execution id cannot be null");
        Objects.requireNonNull(requestId, "Request id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(planId, "Plan id cannot be null");
        Objects.requireNonNull(initiatedBy, "Initiator cannot be null");
        Objects.requireNonNull(startedAt, "Start timestamp cannot be null");
        Objects.requireNonNull(status, "Execution status cannot be null");
        failureReason = failureReason == null ? null : failureReason.trim();
        if (baseRevision < 0 || targetRevision <= baseRevision)
            throw new IllegalArgumentException("Target revision must be greater than non-negative base revision");
        if (status == Status.APPLIED) {
            if (resultingRevision == null || resultingRevision != targetRevision || completedAt == null)
                throw new IllegalArgumentException("Applied execution must record its exact target revision and completion time");
            if (failureReason != null) throw new IllegalArgumentException("Applied execution cannot have a failure reason");
        } else if (status == Status.FAILED || status == Status.CONFLICT) {
            if (completedAt == null || failureReason == null || failureReason.isBlank())
                throw new IllegalArgumentException("Failed or conflicting execution must preserve completion time and reason");
            if (resultingRevision != null)
                throw new IllegalArgumentException("Failed or conflicting execution cannot claim a resulting revision");
        } else if (resultingRevision != null || completedAt != null || failureReason != null) {
            throw new IllegalArgumentException("Applying execution cannot carry completion fields");
        }
        if (completedAt != null && completedAt.isBefore(startedAt))
            throw new IllegalArgumentException("Completion cannot precede execution start");
    }

    public static ScheduleChangeExecution begin(UUID executionId, ScheduleChangeRequest request,
            UUID initiatedBy, Instant startedAt, long livePlanRevision) {
        Objects.requireNonNull(request, "Change request cannot be null");
        Objects.requireNonNull(initiatedBy, "Initiator cannot be null");
        Objects.requireNonNull(startedAt, "Start timestamp cannot be null");
        if (request.status() != ScheduleChangeRequest.Status.APPROVED)
            throw new IllegalStateException("Only an approved schedule change can be executed");
        ScheduleChangeDecision approval = request.decisions().stream()
                .filter(d -> d.outcome() == ScheduleChangeDecision.Outcome.APPROVE)
                .findFirst().orElseThrow(() -> new IllegalStateException("Approved request has no approval evidence"));
        if (livePlanRevision != request.baseRevision())
            throw new IllegalStateException("Plan revision changed after approval; execution must not start");
        if (startedAt.isBefore(approval.decidedAt()))
            throw new IllegalArgumentException("Execution cannot start before approval");
        return new ScheduleChangeExecution(executionId, request.requestId(), request.tenantId(),
                request.structureId(), request.planId(), request.baseRevision(), request.targetRevision(),
                initiatedBy, startedAt, Status.APPLYING, null, null, null);
    }

    /** Reconcile the execution against the persisted current plan snapshot. */
    public ScheduleChangeExecution reconcile(PlanRevisionSnapshot current, Instant reconciledAt) {
        Objects.requireNonNull(current, "Current plan snapshot cannot be null");
        Objects.requireNonNull(reconciledAt, "Reconciliation timestamp cannot be null");
        if (status != Status.APPLYING) {
            if (status == Status.APPLIED && matchesTarget(current)) return this;
            throw new IllegalStateException("Only an applying execution can be reconciled");
        }
        if (reconciledAt.isBefore(startedAt))
            throw new IllegalArgumentException("Reconciliation cannot precede execution start");
        validateScope(current);
        if (current.revision() == targetRevision) {
            if (current.capturedAt().isBefore(startedAt))
                throw new IllegalArgumentException("Target snapshot predates execution start");
            return new ScheduleChangeExecution(executionId, requestId, tenantId, structureId, planId,
                    baseRevision, targetRevision, initiatedBy, startedAt, Status.APPLIED,
                    current.revision(), reconciledAt, null);
        }
        if (current.revision() != baseRevision) {
            return new ScheduleChangeExecution(executionId, requestId, tenantId, structureId, planId,
                    baseRevision, targetRevision, initiatedBy, startedAt, Status.CONFLICT,
                    null, reconciledAt, "Live plan revision is neither the approved base nor target revision");
        }
        throw new IllegalStateException("Target revision is not yet visible; execution remains in progress");
    }

    public ScheduleChangeExecution fail(Instant failedAt, String reason) {
        if (status != Status.APPLYING) throw new IllegalStateException("Only an applying execution can fail");
        Objects.requireNonNull(failedAt, "Failure timestamp cannot be null");
        if (failedAt.isBefore(startedAt)) throw new IllegalArgumentException("Failure cannot precede execution start");
        return new ScheduleChangeExecution(executionId, requestId, tenantId, structureId, planId,
                baseRevision, targetRevision, initiatedBy, startedAt, Status.FAILED,
                null, failedAt, reason);
    }

    private boolean matchesTarget(PlanRevisionSnapshot snapshot) {
        return tenantId.equals(snapshot.tenantId()) && structureId.equals(snapshot.structureId())
                && planId.equals(snapshot.planId()) && snapshot.revision() == targetRevision;
    }

    private void validateScope(PlanRevisionSnapshot snapshot) {
        if (!tenantId.equals(snapshot.tenantId()) || !structureId.equals(snapshot.structureId())
                || !planId.equals(snapshot.planId()))
            throw new IllegalArgumentException("Snapshot belongs to a different tenant, structure, or plan");
    }

    public enum Status { APPLYING, APPLIED, FAILED, CONFLICT }
}
