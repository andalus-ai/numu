package tech.kayys.numou.identity.application.security;

import java.util.Set;
import java.util.UUID;

/** Evidence retrieved by a trusted server-side provider, never deserialized from HTTP. */
public record AuthorizationEvidence(
        UUID principalId,
        UUID tenantId,
        UUID workspaceId,
        Set<String> grantedActions,
        boolean active
) {
    public AuthorizationEvidence {
        grantedActions = grantedActions == null ? Set.of() : Set.copyOf(grantedActions);
    }
}
