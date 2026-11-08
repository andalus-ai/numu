package tech.kayys.numou.agent.application;

import java.util.Objects;
import java.util.Set;

/**
 * Verified caller context supplied by the host authentication adapter.
 *
 * Tenant and actor values must never be populated from untrusted tool arguments.
 */
public record AgentExecutionContext(
        String applicationId,
        String tenantId,
        String actorId,
        Set<String> permissions,
        String correlationId) {

    public AgentExecutionContext {
        applicationId = required(applicationId, "applicationId");
        tenantId = required(tenantId, "tenantId");
        actorId = required(actorId, "actorId");
        correlationId = required(correlationId, "correlationId");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "permissions"));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}