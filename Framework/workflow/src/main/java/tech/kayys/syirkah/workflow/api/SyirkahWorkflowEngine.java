package tech.kayys.syirkah.workflow.api;

import io.smallrye.mutiny.Uni;

import java.util.Map;
import java.util.Optional;

/**
 * Enterprise workflow execution contract in Syirkah, implemented by Gamelan DAG engine.
 */
public interface SyirkahWorkflowEngine {

    void registerDefinition(SyirkahWorkflowDefinition definition);

    Uni<WorkflowExecution> startWorkflow(String definitionId, Map<String, Object> input);

    Uni<WorkflowExecution> resumeWorkflow(String instanceId, String signal, Map<String, Object> payload);

    Optional<WorkflowExecution> getExecution(String instanceId);
}
