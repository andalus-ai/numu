package tech.kayys.numou.workflow.engine;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workflow.api.NumouWorkflowDefinition;
import tech.kayys.numou.workflow.api.WorkflowExecution;
import tech.kayys.numou.workflow.api.WorkflowState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamelanWorkflowEngineAdapterTest {

    @Test
    void approvalResumesAndExecutesDownstreamSteps() {
        GamelanWorkflowEngineAdapter engine = engineWith(new NumouWorkflowDefinition(
                "approval-flow", "Approval flow", "1", "test",
                List.of(
                        step("start", "AUTOMATED", "approval"),
                        step("approval", "HUMAN_APPROVAL", "after-approval"),
                        step("after-approval", "AUTOMATED", "finish"),
                        step("finish", "AUTOMATED")
                )));

        WorkflowExecution suspended = engine.startWorkflow("approval-flow", Map.of("requestId", "r-1"))
                .await().indefinitely();

        assertThat(suspended.state()).isEqualTo(WorkflowState.SUSPENDED);
        assertThat(engine.getExecution(suspended.instanceId())).contains(suspended);

        WorkflowExecution completed = engine.resumeWorkflow(
                suspended.instanceId(), "APPROVED", Map.of("reviewer", "user-1"))
                .await().indefinitely();

        assertThat(completed.state()).isEqualTo(WorkflowState.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.context().get("requestId")).contains("r-1");
        assertThat(completed.context().get("reviewer")).contains("user-1");
        assertThat(engine.getExecution(suspended.instanceId())).contains(completed);
    }

    @Test
    void invalidSignalLeavesWorkflowSuspendedForValidRetry() {
        GamelanWorkflowEngineAdapter engine = engineWith(new NumouWorkflowDefinition(
                "signal-flow", "Signal flow", "1", "test",
                List.of(step("approval", "HUMAN_APPROVAL", "done"), step("done", "AUTOMATED"))));

        WorkflowExecution suspended = engine.startWorkflow("signal-flow", Map.of()).await().indefinitely();

        assertThatThrownBy(() -> engine.resumeWorkflow(suspended.instanceId(), "MAYBE", Map.of())
                .await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(engine.getExecution(suspended.instanceId()).orElseThrow().state())
                .isEqualTo(WorkflowState.SUSPENDED);

        assertThat(engine.resumeWorkflow(suspended.instanceId(), "APPROVE", Map.of())
                .await().indefinitely().state()).isEqualTo(WorkflowState.COMPLETED);
    }

    @Test
    void rejectionFailsWorkflowInsteadOfRunningDownstreamSteps() {
        GamelanWorkflowEngineAdapter engine = engineWith(new NumouWorkflowDefinition(
                "reject-flow", "Reject flow", "1", "test",
                List.of(step("approval", "HUMAN_APPROVAL", "must-not-run"),
                        step("must-not-run", "AUTOMATED"))));

        WorkflowExecution suspended = engine.startWorkflow("reject-flow", Map.of()).await().indefinitely();
        WorkflowExecution failed = engine.resumeWorkflow(suspended.instanceId(), "REJECT", Map.of())
                .await().indefinitely();

        assertThat(failed.state()).isEqualTo(WorkflowState.FAILED);
        assertThat(failed.completedAt()).isNotNull();
        assertThatThrownBy(() -> engine.resumeWorkflow(suspended.instanceId(), "APPROVE", Map.of())
                .await().indefinitely())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsCyclesEvenWhenTheyAreBehindAnApprovalStep() {
        GamelanWorkflowEngineAdapter engine = engineWith(new NumouWorkflowDefinition(
                "cyclic-flow", "Cyclic flow", "1", "test",
                List.of(
                        step("approval", "HUMAN_APPROVAL", "after"),
                        step("after", "AUTOMATED", "cycle-a"),
                        step("cycle-a", "AUTOMATED", "cycle-b"),
                        step("cycle-b", "AUTOMATED", "cycle-a")
                )));

        assertThatThrownBy(() -> engine.startWorkflow("cyclic-flow", Map.of()).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void joinNodeIsNotReadyUntilEveryPredecessorCompletes() {
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        adjacency.put("left", Set.of("join"));
        adjacency.put("right", Set.of("join"));
        adjacency.put("join", Set.of());
        WorkflowDagScheduler scheduler = new WorkflowDagScheduler(adjacency, List.of("left", "right", "join"));

        assertThat(scheduler.nextReady()).isEqualTo("left");
        scheduler.onNodeCompleted("left");
        assertThat(scheduler.nextReady()).isEqualTo("right");

        scheduler.onNodeCompleted("right");
        assertThat(scheduler.nextReady()).isEqualTo("join");
        scheduler.onNodeCompleted("join");
        assertThat(scheduler.isComplete()).isTrue();
    }

    private static GamelanWorkflowEngineAdapter engineWith(NumouWorkflowDefinition definition) {
        GamelanWorkflowEngineAdapter engine = new GamelanWorkflowEngineAdapter();
        engine.registerDefinition(definition);
        return engine;
    }

    private static NumouWorkflowDefinition.WorkflowStep step(String id, String type, String... next) {
        return new NumouWorkflowDefinition.WorkflowStep(id, type, "test-handler", List.of(next));
    }
}
