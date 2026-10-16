package tech.kayys.syirkah.observability;

import java.util.*;

/**
 * Default implementation of HealthCheckRegistry.
 */
public final class DefaultHealthCheckRegistry implements HealthCheckRegistry {

    private final Map<String, HealthCheck> checks = new LinkedHashMap<>();
    private boolean frozen;

    @Override
    public synchronized void register(HealthCheck check) {
        Objects.requireNonNull(check, "check cannot be null");
        if (frozen) {
            throw new IllegalStateException("Health check registry is frozen");
        }
        if (checks.containsKey(check.id())) {
            throw new IllegalArgumentException("Duplicate health check ID: " + check.id());
        }
        checks.put(check.id(), check);
    }

    @Override
    public synchronized void unregister(String id) {
        if (frozen) {
            throw new IllegalStateException("Health check registry is frozen");
        }
        checks.remove(id);
    }

    @Override
    public synchronized Optional<HealthCheck> find(String id) {
        return Optional.ofNullable(checks.get(id));
    }

    @Override
    public synchronized List<HealthCheck> checks() {
        return List.copyOf(checks.values());
    }

    @Override
    public synchronized boolean isFrozen() {
        return frozen;
    }

    @Override
    public synchronized void freeze() {
        this.frozen = true;
    }
}
