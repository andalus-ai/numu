package tech.kayys.syirkah.accounting.domain.workflow;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Execution context dictionary passed across nodes. */
public final class ProcessContext {

    private final Map<String, Object> data;

    public ProcessContext() { this.data = new HashMap<>(); }
    public ProcessContext(Map<String, Object> data) { this.data = new HashMap<>(data); }

    public static ProcessContext empty() { return new ProcessContext(); }

    public ProcessContext with(String key, Object value) {
        Map<String, Object> copy = new HashMap<>(data);
        copy.put(key, value);
        return new ProcessContext(copy);
    }

    public Optional<Object> get(String key) { return Optional.ofNullable(data.get(key)); }
    public <T> Optional<T> get(String key, Class<T> type) {
        Object val = data.get(key);
        if (val != null && type.isInstance(val)) return Optional.of(type.cast(val));
        return Optional.empty();
    }
    public Map<String, Object> toMap() { return Collections.unmodifiableMap(data); }
}
