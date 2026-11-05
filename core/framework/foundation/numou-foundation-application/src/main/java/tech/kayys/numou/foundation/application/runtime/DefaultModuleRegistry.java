package tech.kayys.numou.foundation.application.runtime;

import java.util.*;

/**
 * Default implementation of ModuleRegistry.
 */
public final class DefaultModuleRegistry implements ModuleRegistry {

    private final Map<String, ApplicationModule> modules = new LinkedHashMap<>();

    @Override
    public synchronized void register(ApplicationModule module) {
        Objects.requireNonNull(module, "module cannot be null");
        String id = module.id();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("module ID cannot be blank");
        }
        if (modules.containsKey(id)) {
            throw new IllegalStateException("Application module is already registered: " + id);
        }
        modules.put(id, module);
    }

    @Override
    public synchronized Optional<ApplicationModule> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(modules.get(id));
    }

    @Override
    public synchronized Set<ApplicationModule> modules() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(modules.values()));
    }
}
