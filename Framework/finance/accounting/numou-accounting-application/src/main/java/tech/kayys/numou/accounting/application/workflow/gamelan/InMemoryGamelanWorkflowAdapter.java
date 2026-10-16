
package tech.kayys.syirkah.accounting.application.workflow.gamelan;

import io.smallrye.mutiny.Uni;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory adapter simulating Gamelan workflow execution for standalone/test environments.
 */
public class InMemoryGamelanWorkflowAdapter implements GamelanWorkflowClient {

    private final Map<String, WorkflowInstanceStatus> instances = new ConcurrentHashMap<>();

    @Override
    public Uni<String> startProcess(WorkflowStartRequest request) {
        String instanceId = "gamelan-proc-" + UUID.randomUUID();
        instances.put(instanceId, WorkflowInstanceStatus.RUNNING);
        return Uni.createFrom().item(instanceId);
    }

    @Override
    public Uni<WorkflowInstanceStatus> getProcessStatus(String instanceId) {
        WorkflowInstanceStatus status = instances.getOrDefault(instanceId, WorkflowInstanceStatus.COMPLETED);
        return Uni.createFrom().item(status);
    }

    @Override
    public Uni<Void> completeTask(String taskId, Map<String, Object> taskVariables) {
        return Uni.createFrom().voidItem();
    }
}
