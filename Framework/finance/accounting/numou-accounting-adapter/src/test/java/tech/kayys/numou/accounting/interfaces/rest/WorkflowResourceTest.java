package tech.kayys.syirkah.accounting.interfaces.rest;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.application.workflow.WorkflowEngine;
import tech.kayys.syirkah.accounting.domain.workflow.*;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowResourceTest {

    private WorkflowResource resource;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        engine = new WorkflowEngine();
        resource = new WorkflowResource();
        resource.workflowEngine = engine;

        // Register a simple workflow: Start -> TaskNode(MANAGER) -> End
        StartNode start = StartNode.of("start", "approval");
        TaskNode taskNode = new TaskNode(NodeId.of("approval"), "Approve Invoice", "MANAGER", Set.of(NodeId.of("end")));
        EndNode end = EndNode.of("end");

        ProcessDefinition def = new ProcessDefinition(
                ProcessDefinitionId.of("AP_APPROVAL_PROC", 1),
                "AP Invoice Approval",
                Map.of(start.id(), start, taskNode.id(), taskNode, end.id(), end),
                start.id()
        );
        engine.registerDefinition(def);
    }

    @Test
    void testWorkflowExecution() {
        var startReq = new WorkflowResource.StartProcessRequest(
                "AP_APPROVAL_PROC", 1, Map.of("invoiceAmount", 5000));
        Response startResp = resource.startProcess(startReq).await().indefinitely();
        assertEquals(201, startResp.getStatus());
        ProcessInstance instance = (ProcessInstance) startResp.getEntity();
        // After Start node fires, engine stops at the human task -> WAITING
        assertEquals(ProcessStatus.WAITING, instance.status());

        // Find the created task and complete it
        TaskId createdTask = engine.getAllTasks().keySet().iterator().next();
        var completeReq = new WorkflowResource.CompleteTaskRequest(
                "finance_manager", Map.of("approved", true));
        Response compResp = resource.completeTask(createdTask.value(), completeReq).await().indefinitely();
        assertEquals(200, compResp.getStatus());

        Response instResp = resource.getInstance(instance.id().value()).await().indefinitely();
        assertEquals(200, instResp.getStatus());
        ProcessInstance updated = (ProcessInstance) instResp.getEntity();
        assertEquals(ProcessStatus.COMPLETED, updated.status());
    }
}
