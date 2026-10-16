package tech.kayys.syirkah.identity.application.security.abac;

import java.util.Objects;
import java.util.UUID;

public record AuthorizationPolicy(
        UUID id,
        String tenantId,
        String permission,
        PolicyEffect effect,
        PolicyCondition condition,
        boolean active,
        long version
) {
    public AuthorizationPolicy {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(permission, "permission cannot be null");
        Objects.requireNonNull(effect, "effect cannot be null");
        Objects.requireNonNull(condition, "condition cannot be null");
        if (tenantId.isBlank() || permission.isBlank() || version < 1) {
            throw new IllegalArgumentException("Policy tenant, permission, and version must be valid");
        }
    }
}
