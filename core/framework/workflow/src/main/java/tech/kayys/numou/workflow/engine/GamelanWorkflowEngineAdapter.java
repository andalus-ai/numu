package tech.kayys.numou.workflow.engine;

import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.numou.workflow.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAG-based workflow engine adapter.
 *
 * The scheduler is implemented in this module so the framework can build
 * without requiring Gamelan artifacts from an authenticated package registry.
 */
public class GamelanWorkflowEngineAdapter implements NumouWorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(GamelanWorkflowEngineAdapter.class);

    private final Map<String, NumouWorkflowDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, WorkflowExecution> executions = new ConcurrentHashMap<>();
    private final Map<String, RuntimeState> runtimeStates = new ConcurrentHashMap<>();

    @Override
    public void registerDefinition(NumouWorkflowDefinition definition) {
        if (definition != null) {
            if (definition.id().isBlank()) {
                throw new IllegalArgumentException("Workflow definition ID must not be blank");
            }
            definitions.put(definition.id(), definition);
            log.info("Registered workflow definition: {} (version {})", definition.id(), definition.version());
        }
    }

    @Override
    public Uni<WorkflowExecution> startWorkflow(String definitionId, Map<String, Object> input) {
        return Uni.createFrom().item(() -> {
            NumouWorkflowDefinition def = definitions.get(definitionId);
            if (def == null) {
                throw new IllegalArgumentException("Workflow definition not found: " + definitionId);
            }

            String instanceId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            WorkflowContext context = WorkflowContext.of(input);

            Map<String, Set<String>> adjacency = new LinkedHashMap<>();
            List<String> nodes = new ArrayList<>();
            Map<String, NumouWorkflowDefinition.WorkflowStep> stepsById = new LinkedHashMap<>();
            for (NumouWorkflowDefinition.WorkflowStep step : def.steps()) {
                if (step == null) {
                    throw new IllegalArgumentException("Workflow steps must not contain null");
                }
                if (step.stepId() == null || step.stepId().isBlank()) {
                    throw new IllegalArgumentException("Workflow step ID must not be blank");
                }
                if (stepsById.putIfAbsent(step.stepId(), step) != null) {
                    throw new IllegalArgumentException("Duplicate workflow step id: " + step.stepId());
                }
                nodes.add(step.stepId());
                adjacency.put(step.stepId(), step.nextStepIds() == null
                        ? Set.of()
                        : new LinkedHashSet<>(step.nextStepIds()));
            }

            WorkflowDagScheduler scheduler = new WorkflowDagScheduler(adjacency, nodes);
            RuntimeState runtime = new RuntimeState(definitionId, scheduler, stepsById, context, now);
            runtimeStates.put(instanceId, runtime);

            log.info("Started workflow instance {} for definition {}", instanceId, definitionId);
            WorkflowExecution execution = advance(instanceId, runtime);
            executions.put(instanceId, execution);
            if (execution.state() == WorkflowState.COMPLETED || execution.state() == WorkflowState.FAILED) {
                runtimeStates.remove(instanceId, runtime);
            }
            return execution;
        });
    }

    @Override
    public Uni<WorkflowExecution> resumeWorkflow(String instanceId, String signal, Map<String, Object> payload) {
        return Uni.createFrom().item(() -> {
            WorkflowExecution current = executions.get(instanceId);
            if (current == null) {
                throw new IllegalArgumentException("Workflow instance not found: " + instanceId);
            }
            if (current.state() != WorkflowState.SUSPENDED) {
                throw new IllegalStateException("Workflow instance " + instanceId + " is not suspended");
            }

            RuntimeState runtime = runtimeStates.get(instanceId);
            if (runtime == null) {
                throw new IllegalStateException("Workflow runtime state is unavailable for instance " + instanceId);
            }

            synchronized (runtime) {
                // Recheck after acquiring the runtime lock so concurrent signals cannot
                // approve the same human task twice.
                current = executions.get(instanceId);
                if (current == null || current.state() != WorkflowState.SUSPENDED) {
                    throw new IllegalStateException("Workflow instance " + instanceId + " is not suspended");
                }
                if (runtime.pendingApprovalStepId == null) {
                    throw new IllegalStateException("Suspended workflow has no pending approval step: " + instanceId);
                }

                String normalizedSignal = normalizeSignal(signal);
                if (!"APPROVE".equals(normalizedSignal) && !"REJECT".equals(normalizedSignal)) {
                    throw new IllegalArgumentException(
                            "Unsupported workflow signal '" + signal + "'. Expected APPROVE or REJECT");
                }

                WorkflowContext updatedContext = runtime.context;
                if (payload != null) {
                    for (Map.Entry<String, Object> entry : payload.entrySet()) {
                        updatedContext = updatedContext.with(entry.getKey(), entry.getValue());
                    }
                }
                runtime.context = updatedContext;

                if ("REJECT".equals(normalizedSignal)) {
                    WorkflowExecution failed = new WorkflowExecution(
                            current.instanceId(),
                            current.definitionId(),
                            WorkflowState.FAILED,
                            updatedContext,
                            current.startedAt(),
                            Instant.now());
                    executions.put(instanceId, failed);
                    runtimeStates.remove(instanceId, runtime);
                    return failed;
                }

                String approvedStepId = runtime.pendingApprovalStepId;
                runtime.scheduler.onNodeCompleted(approvedStepId);
                runtime.pendingApprovalStepId = null;

                WorkflowExecution resumed = advance(instanceId, runtime);
                executions.put(instanceId, resumed);
                if (resumed.state() == WorkflowState.COMPLETED || resumed.state() == WorkflowState.FAILED) {
                    runtimeStates.remove(instanceId, runtime);
                }
                return resumed;
            }
        });
    }

    private WorkflowExecution advance(String instanceId, RuntimeState runtime) {
        String nextNode;
        while ((nextNode = runtime.scheduler.nextReady()) != null) {
            NumouWorkflowDefinition.WorkflowStep step = runtime.stepsById.get(nextNode);
            if ("HUMAN_APPROVAL".equalsIgnoreCase(step.stepType())) {
                runtime.pendingApprovalStepId = nextNode;
                log.info("Workflow {} suspended at step {} awaiting human approval signal", instanceId, nextNode);
                return new WorkflowExecution(
                        instanceId,
                        runtime.definitionId,
                        WorkflowState.SUSPENDED,
                        runtime.context,
                        runtime.startedAt,
                        null);
            }
            runtime.scheduler.onNodeCompleted(nextNode);
        }

        if (!runtime.scheduler.isComplete()) {
            return new WorkflowExecution(
                    instanceId,
                    runtime.definitionId,
                    WorkflowState.FAILED,
                    runtime.context,
                    runtime.startedAt,
                    Instant.now());
        }

        return new WorkflowExecution(
                instanceId,
                runtime.definitionId,
                WorkflowState.COMPLETED,
                runtime.context,
                runtime.startedAt,
                Instant.now());
    }

    private static String normalizeSignal(String signal) {
        if (signal == null || signal.isBlank()) {
            return "";
        }
        return switch (signal.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED" -> "APPROVE";
            case "REJECT", "REJECTED" -> "REJECT";
            default -> "";
        };
    }

    @Override
    public Optional<WorkflowExecution> getExecution(String instanceId) {
        if (instanceId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(executions.get(instanceId));
    }

    private static final class RuntimeState {
        private final String definitionId;
        private final WorkflowDagScheduler scheduler;
        private final Map<String, NumouWorkflowDefinition.WorkflowStep> stepsById;
        private final Instant startedAt;
        private WorkflowContext context;
        private String pendingApprovalStepId;

        private RuntimeState(
                String definitionId,
                WorkflowDagScheduler scheduler,
                Map<String, NumouWorkflowDefinition.WorkflowStep> stepsById,
                WorkflowContext context,
                Instant startedAt) {
            this.definitionId = definitionId;
            this.scheduler = scheduler;
            this.stepsById = Map.copyOf(stepsById);
            this.context = context;
            this.startedAt = startedAt;
        }
    }
}
