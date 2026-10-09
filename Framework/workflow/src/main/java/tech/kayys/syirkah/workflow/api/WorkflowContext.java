package tech.kayys.syirkah.workflow.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Execution context carrying state and parameters for a running workflow.
 */
public record WorkflowContext(Map<String, Object> data) {

    public WorkflowContext {
        data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(data));
    }

    public static WorkflowContext empty() {
        return new WorkflowContext(Collections.emptyMap());
    }

    public static WorkflowContext of(Map<String, Object> data) {
        return new WorkflowContext(data);
    }

    public WorkflowContext with(String key, Object value) {
        Map<String, Object> next = new HashMap<>(data);
        next.put(key, value);
        return new WorkflowContext(next);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) data.get(key));
    }
}
