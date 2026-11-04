package tech.kayys.numou.applicationplatform.application.extensions;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe host-composition registry. Re-registration of the same definition is
 * idempotent; replacing a contract requires an explicit versioned host deployment.
 */
public final class InMemoryApplicationExtensionPointRegistry implements ApplicationExtensionPointRegistry {
    private final ConcurrentMap<String, ApplicationExtensionPoint> points = new ConcurrentHashMap<>();

    @Override
    public void register(ApplicationExtensionPoint extensionPoint) {
        Objects.requireNonNull(extensionPoint, "extensionPoint");
        points.compute(extensionPoint.id(), (id, existing) -> {
            if (existing != null && !existing.equals(extensionPoint))
                throw new IllegalStateException("Extension point already registered with a different contract: " + id);
            return extensionPoint;
        });
    }

    @Override
    public boolean unregister(String id, String expectedContractVersion) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(expectedContractVersion, "expectedContractVersion");
        return points.computeIfPresent(id, (key, current) ->
                current.contractVersion().equals(expectedContractVersion) ? null : current) == null;
    }

    @Override
    public Optional<ApplicationExtensionPoint> find(String id) {
        return Optional.ofNullable(points.get(id));
    }

    @Override
    public List<ApplicationExtensionPoint> all() {
        return points.values().stream().sorted(Comparator.comparing(ApplicationExtensionPoint::id)).toList();
    }
}
