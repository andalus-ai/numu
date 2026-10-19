package tech.kayys.numou.workflow;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.workflow.api.NumouWorkflowDefinition;
import tech.kayys.numou.workflow.api.WorkflowExecution;
import tech.kayys.numou.workflow.api.WorkflowState;
import tech.kayys.numou.workflow.engine.GamelanWorkflowEngineAdapter;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GamelanWorkflowEngineAdapterTest {

    @Test
    void shouldExecuteLinearDagWorkflowToCompletion() {
        GamelanWorkflowEngineAdapter engine = new GamelanWorkflowEngineAdapter();

        NumouWorkflowDefinition def = new NumouWorkflowDefinition(
                "invoice-approval",
                "Invoice Approval Workflow",
                "1.0.0",
                "Standard 2-step invoice processing",
                List.of(
                        new NumouWorkflowDefinition.WorkflowStep("step-1", "VALIDATION", "validateInvoice", List.of("step-2")),
                        new NumouWorkflowDefinition.WorkflowStep("step-2", "POSTING", "postToLedger", List.of())
                )
        );

        engine.registerDefinition(def);

        WorkflowExecution execution = engine.startWorkflow("invoice-approval", Map.of("invoiceId", "INV-001"))
                .await().atMost(Duration.ofSeconds(5));

        assertThat(execution).isNotNull();
        assertThat(execution.state()).isEqualTo(WorkflowState.COMPLETED);
        assertThat(execution.context().<String>get("invoiceId")).contains("INV-001");
    }

    @Test
    void shouldSuspendWorkflowOnHumanTaskAndResumeOnSignal() {
        GamelanWorkflowEngineAdapter engine = new GamelanWorkflowEngineAdapter();

        NumouWorkflowDefinition def = new NumouWorkflowDefinition(
                "purchase-order",
                "Purchase Order Approval",
                "1.0.0",
                "Workflow with human approval",
                List.of(
                        new NumouWorkflowDefinition.WorkflowStep("step-1", "HUMAN_APPROVAL", "managerApproval", List.of("step-2")),
                        new NumouWorkflowDefinition.WorkflowStep("step-2", "DISPATCH", "dispatchOrder", List.of())
                )
        );

        engine.registerDefinition(def);

        WorkflowExecution suspended = engine.startWorkflow("purchase-order", Map.of("poId", "PO-999"))
                .await().atMost(Duration.ofSeconds(5));

        assertThat(suspended.state()).isEqualTo(WorkflowState.SUSPENDED);

        WorkflowExecution resumed = engine.resumeWorkflow(suspended.instanceId(), "APPROVED", Map.of("approvedBy", "manager1"))
                .await().atMost(Duration.ofSeconds(5));

        assertThat(resumed.state()).isEqualTo(WorkflowState.COMPLETED);
        assertThat(resumed.context().<String>get("approvedBy")).contains("manager1");
    }
}
