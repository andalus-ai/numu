package tech.kayys.syirkah.observability;

import java.util.List;
import java.util.Optional;

/**
 * Registry holding available health checks (config02.md §P4-07 #10).
 */
public interface HealthCheckRegistry {

    void register(HealthCheck check);

    void unregister(String id);

    Optional<HealthCheck> find(String id);

    List<HealthCheck> checks();

    boolean isFrozen();

    void freeze();
}
