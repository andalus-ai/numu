package tech.kayys.numou.workflow.engine;

import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.gamelan.dag.DagScheduler;
import tech.kayys.numou.workflow.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise workflow engine adapter powered by Gamelan DAG scheduler.
 */
public class GamelanWorkflowEngineAdapter implements NumouWorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(GamelanWorkflowEngineAdapter.class);

    private final Map<String, NumouWorkflowDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, WorkflowExecution> executions = new ConcurrentHashMap<>();

    @Override
    public void registerDefinition(NumouWorkflowDefinition definition) {
        if (definition != null) {
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
            WorkflowContext ctx = WorkflowContext.of(input);

            // Build DAG adjacency from steps
            Map<String, Set<String>> adjacency = new HashMap<>();
            List<String> nodes = new ArrayList<>();
            for (NumouWorkflowDefinition.WorkflowStep step : def.steps()) {
                nodes.add(step.stepId());
                adjacency.put(step.stepId(), new HashSet<>(step.nextStepIds()));
            }

            DagScheduler scheduler = new DagScheduler(adjacency, nodes);
            log.info("Started workflow instance {} for definition {}", instanceId, definitionId);

            // Step through DAG ready nodes
            String nextNode = scheduler.nextReady();
            boolean suspendedForHumanTask = false;

            while (nextNode != null) {
                final String current = nextNode;
                NumouWorkflowDefinition.WorkflowStep step = def.steps().stream()
                        .filter(s -> s.stepId().equals(current))
                        .findFirst()
                        .orElse(null);

                if (step != null && "HUMAN_APPROVAL".equalsIgnoreCase(step.stepType())) {
                    suspendedForHumanTask = true;
                    log.info("Workflow {} suspended at step {} awaiting human approval signal", instanceId, current);
                    break;
                }

                scheduler.onNodeCompleted(current, adjacency);
                nextNode = scheduler.nextReady();
            }

            WorkflowState finalState = suspendedForHumanTask ? WorkflowState.SUSPENDED : WorkflowState.COMPLETED;
            WorkflowExecution exec = new WorkflowExecution(
                    instanceId,
                    definitionId,
                    finalState,
                    ctx,
                    now,
                    finalState == WorkflowState.COMPLETED ? Instant.now() : null
            );

            executions.put(instanceId, exec);
            return exec;
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

            log.info("Resuming workflow instance {} with signal {}", instanceId, signal);
            WorkflowContext updatedContext = current.context();
            if (payload != null) {
                for (Map.Entry<String, Object> e : payload.entrySet()) {
                    updatedContext = updatedContext.with(e.getKey(), e.getValue());
                }
            }

            WorkflowExecution completed = new WorkflowExecution(
                    current.instanceId(),
                    current.definitionId(),
                    WorkflowState.COMPLETED,
                    updatedContext,
                    current.startedAt(),
                    Instant.now()
            );

            executions.put(instanceId, completed);
            return completed;
        });
    }

    @Override
    public Optional<WorkflowExecution> getExecution(String instanceId) {
        if (instanceId == null) return Optional.empty();
        return Optional.ofNullable(executions.get(instanceId));
    }
}
