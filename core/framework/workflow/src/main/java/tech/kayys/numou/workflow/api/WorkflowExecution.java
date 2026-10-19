package tech.kayys.numou.workflow.api;

import java.time.Instant;

/**
 * Snapshot of a running or completed workflow instance.
 */
public record WorkflowExecution(
        String instanceId,
        String definitionId,
        WorkflowState state,
        WorkflowContext context,
        Instant startedAt,
        Instant completedAt
) {}
