
package tech.kayys.syirkah.accounting.application.workflow.gamelan;

import io.smallrye.mutiny.Uni;

import java.util.Map;

/**
 * Port for orchestrating workflows with Families/gamelan workflow engine.
 */
public interface GamelanWorkflowClient {

    /** Start a workflow process in Gamelan (e.g., PO approval, Period Close). */
    Uni<String> startProcess(WorkflowStartRequest request);

    /** Query process status from Gamelan. */
    Uni<WorkflowInstanceStatus> getProcessStatus(String instanceId);

    /** Complete a human task inside Gamelan. */
    Uni<Void> completeTask(String taskId, Map<String, Object> taskVariables);
}
