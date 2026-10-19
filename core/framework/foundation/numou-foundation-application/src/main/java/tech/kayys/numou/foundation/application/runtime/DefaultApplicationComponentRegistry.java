package tech.kayys.numou.foundation.application.runtime;

import java.util.*;

/**
 * Default implementation of ApplicationComponentRegistry.
 */
public final class DefaultApplicationComponentRegistry implements ApplicationComponentRegistry {

    private final Map<String, RuntimeComponent> components = new LinkedHashMap<>();

    @Override
    public synchronized void register(RuntimeComponent component) {
        Objects.requireNonNull(component, "component cannot be null");
        components.put(component.id(), component);
    }

    @Override
    public synchronized Optional<RuntimeComponent> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(components.get(id));
    }

    @Override
    public synchronized Set<RuntimeComponent> components() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(components.values()));
    }
}
