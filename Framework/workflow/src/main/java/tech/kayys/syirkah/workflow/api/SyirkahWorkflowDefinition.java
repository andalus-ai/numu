package tech.kayys.syirkah.workflow.api;

import java.util.List;
import java.util.Objects;

/**
 * Definition of a business workflow composed of ordered steps or DAG nodes.
 */
public record SyirkahWorkflowDefinition(
        String id,
        String name,
        String version,
        String description,
        List<WorkflowStep> steps
) {
    public SyirkahWorkflowDefinition {
        Objects.requireNonNull(id, "Workflow definition ID must not be null");
        Objects.requireNonNull(name, "Workflow definition name must not be null");
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public record WorkflowStep(
            String stepId,
            String stepType,
            String handlerName,
            List<String> nextStepIds
    ) {}
}
