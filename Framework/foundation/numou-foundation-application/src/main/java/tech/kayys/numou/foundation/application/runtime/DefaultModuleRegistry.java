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
        modules.put(module.id(), module);
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
