package tech.kayys.syirkah.accounting.application.workflow;

import tech.kayys.syirkah.accounting.domain.workflow.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight in-process workflow execution engine.
 * Capable of stepping process instances through node transitions.
 */
public final class WorkflowEngine {

    private final Map<ProcessDefinitionId, ProcessDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<ProcessInstanceId, ProcessInstance> instances = new ConcurrentHashMap<>();
    private final Map<TaskId, Task> tasks = new ConcurrentHashMap<>();

    public void registerDefinition(ProcessDefinition def) {
        definitions.put(def.id(), def);
    }

    public ProcessInstance startProcess(ProcessDefinitionId defId, ProcessContext initialContext) {
        ProcessDefinition def = definitions.get(defId);
        if (def == null) throw new IllegalArgumentException("Unknown ProcessDefinition: " + defId);

        ProcessInstance instance = new ProcessInstance(
                ProcessInstanceId.generate(), defId, def.startNodeId(), initialContext);
        instances.put(instance.id(), instance);

        step(instance, def);
        return instance;
    }

    public void completeTask(TaskId taskId, String user, Map<String, Object> taskOutput) {
        Task task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("Unknown Task: " + taskId);
        task.complete(user);

        ProcessInstance instance = instances.get(task.processInstanceId());
        if (instance != null) {
            ProcessContext ctx = instance.context();
            if (taskOutput != null) {
                for (Map.Entry<String, Object> e : taskOutput.entrySet()) {
                    ctx = ctx.with(e.getKey(), e.getValue());
                }
            }
            instance.updateContext(ctx);
            instance.resume();

            ProcessDefinition def = definitions.get(instance.definitionId());
            WorkflowNode node = def.getNode(instance.currentNodeId());
            if (!node.outgoing().isEmpty()) {
                NodeId next = node.outgoing().iterator().next();
                instance.advanceTo(next);
                step(instance, def);
            }
        }
    }

    private void step(ProcessInstance instance, ProcessDefinition def) {
        while (instance.status() == ProcessStatus.RUNNING) {
            WorkflowNode node = def.getNode(instance.currentNodeId());
            if (node instanceof StartNode start) {
                NodeId next = start.outgoing().iterator().next();
                instance.advanceTo(next);
            } else if (node instanceof EndNode) {
                instance.complete();
            } else if (node instanceof TaskNode tn) {
                Task task = new Task(TaskId.generate(), instance.id(), tn.id(), tn.assigneeRole());
                tasks.put(task.id(), task);
                instance.pauseForTask();
            } else if (node instanceof DecisionNode dn) {
                boolean cond = evaluate(dn.expression(), instance.context());
                NodeId next = cond ? dn.trueBranch() : dn.falseBranch();
                instance.advanceTo(next);
            } else if (node instanceof CommandNode cn) {
                NodeId next = cn.outgoing().iterator().next();
                instance.advanceTo(next);
            } else {
                break;
            }
        }
    }

    private boolean evaluate(String expr, ProcessContext ctx) {
        // Simple evaluator: key==value or key>number or boolean value lookup
        if (ctx.get(expr).isPresent()) {
            Object v = ctx.get(expr).get();
            if (v instanceof Boolean b) return b;
        }
        if (expr.contains("==")) {
            String[] parts = expr.split("==");
            String k = parts[0].trim();
            String expected = parts[1].trim();
            return ctx.get(k).map(v -> v.toString().equals(expected)).orElse(false);
        }
        return false;
    }

    public ProcessInstance getInstance(ProcessInstanceId id) { return instances.get(id); }
    public Task getTask(TaskId id) { return tasks.get(id); }
    public Map<TaskId, Task> getAllTasks() { return Map.copyOf(tasks); }
}
