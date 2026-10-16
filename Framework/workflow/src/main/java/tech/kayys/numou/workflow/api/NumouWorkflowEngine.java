package tech.kayys.numou.workflow.api;

import io.smallrye.mutiny.Uni;

import java.util.Map;
import java.util.Optional;

/**
 * Enterprise workflow execution contract in Numou, implemented by Gamelan DAG engine.
 */
public interface NumouWorkflowEngine {

    void registerDefinition(NumouWorkflowDefinition definition);

    Uni<WorkflowExecution> startWorkflow(String definitionId, Map<String, Object> input);

    Uni<WorkflowExecution> resumeWorkflow(String instanceId, String signal, Map<String, Object> payload);

    Optional<WorkflowExecution> getExecution(String instanceId);
}
