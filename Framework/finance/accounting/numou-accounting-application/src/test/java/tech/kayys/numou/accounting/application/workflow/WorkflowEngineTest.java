package tech.kayys.syirkah.accounting.application.workflow;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.accounting.domain.workflow.*;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowEngineTest {

    @Test
    void execute_simple_process_to_completion() {
        var start = StartNode.of("start", "end");
        var end = EndNode.of("end");
        var defId = ProcessDefinitionId.of("SIMPLE", 1);
        var def = new ProcessDefinition(defId, "Simple Flow",
                Map.of(start.id(), start, end.id(), end), start.id());

        var engine = new WorkflowEngine();
        engine.registerDefinition(def);

        var instance = engine.startProcess(defId, ProcessContext.empty());

        assertEquals(ProcessStatus.COMPLETED, instance.status());
        assertEquals(end.id(), instance.currentNodeId());
    }

    @Test
    void human_task_pauses_and_completes() {
        var start = StartNode.of("start", "task1");
        var taskNode = new TaskNode(NodeId.of("task1"), "Review Invoice", "ACCOUNTS_OFFICER", Set.of(NodeId.of("end")));
        var end = EndNode.of("end");
        var defId = ProcessDefinitionId.of("TASK_FLOW", 1);
        var def = new ProcessDefinition(defId, "Review Flow",
                Map.of(start.id(), start, taskNode.id(), taskNode, end.id(), end), start.id());

        var engine = new WorkflowEngine();
        engine.registerDefinition(def);

        var instance = engine.startProcess(defId, ProcessContext.empty());
        assertEquals(ProcessStatus.WAITING, instance.status());
        assertEquals(taskNode.id(), instance.currentNodeId());

        TaskId createdTaskId = engine.getAllTasks().keySet().iterator().next();
        Task task = engine.getTask(createdTaskId);
        assertEquals(TaskStatus.OPEN, task.status());

        // Complete the task
        engine.completeTask(createdTaskId, "analyst-1", Map.of("approved", true));

        assertEquals(TaskStatus.COMPLETED, engine.getTask(createdTaskId).status());
        assertEquals(ProcessStatus.COMPLETED, engine.getInstance(instance.id()).status());
    }

    @Test
    void decision_node_branches_correctly() {
        var start = StartNode.of("start", "decide");
        var decision = new DecisionNode(NodeId.of("decide"), "approved", NodeId.of("end_approved"), NodeId.of("end_rejected"));
        var endApprove = EndNode.of("end_approved");
        var endReject = EndNode.of("end_rejected");

        var defId = ProcessDefinitionId.of("DECISION_FLOW", 1);
        var def = new ProcessDefinition(defId, "Decision Flow",
                Map.of(start.id(), start, decision.id(), decision, endApprove.id(), endApprove, endReject.id(), endReject), start.id());

        var engine = new WorkflowEngine();
        engine.registerDefinition(def);

        var inst1 = engine.startProcess(defId, ProcessContext.empty().with("approved", true));
        assertEquals(endApprove.id(), inst1.currentNodeId());
        assertEquals(ProcessStatus.COMPLETED, inst1.status());

        var inst2 = engine.startProcess(defId, ProcessContext.empty().with("approved", false));
        assertEquals(endReject.id(), inst2.currentNodeId());
        assertEquals(ProcessStatus.COMPLETED, inst2.status());
    }
}
