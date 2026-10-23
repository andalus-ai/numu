package tech.kayys.numou.planning.application.port;

import java.util.Optional;
import java.util.UUID;

import tech.kayys.numou.planning.domain.ScheduleChangeExecution;
import tech.kayys.numou.planning.domain.ScheduleChangeRequest;
import tech.kayys.numou.planning.domain.SchedulePlan;

/**
 * Transaction boundary for applying an approved schedule revision.
 *
 * Implementations must atomically compare the persisted plan revision with the request's base
 * revision, persist the target plan and execution receipt together, and enforce execution-ID
 * idempotency. A stale base must return a CONFLICT receipt without changing the plan. A replay
 * with the same execution ID and same request/target must return the original receipt; conflicting
 * reuse of an execution ID must fail. Tenant/structure ownership must be verified by the adapter.
 */
public interface ScheduleChangeExecutionStore {
    Optional<ScheduleChangeExecution> findByExecutionId(UUID executionId);

    /**
     * Atomically applies targetPlan and persists the resulting execution receipt.
     *
     * @param request approved request and its immutable impact evidence
     * @param targetPlan fully materialized target plan at request.targetRevision()
     * @param applyingExecution proposed APPLYING receipt created by the application use case
     * @return the durable terminal receipt, normally APPLIED or CONFLICT
     */
    ScheduleChangeExecution applyAtomically(
            ScheduleChangeRequest request,
            SchedulePlan targetPlan,
            ScheduleChangeExecution applyingExecution);
}
