package tech.kayys.numou.agent.application;

import java.util.Set;

/**
 * Tenant-scoped projection of the host's validated application composition.
 * Implementations must return only capabilities active for this application
 * installation and tenant; caller-supplied tool arguments are never consulted.
 */
@FunctionalInterface
public interface ApplicationCapabilitySelectionPort {
    Set<String> findEnabledCapabilityIds(String applicationId, String tenantId);
}
