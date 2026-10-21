package tech.kayys.numou.identity.application.security;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A deny-by-default, immutable input to the identity authorization boundary. */
public record AuthorizationRequest(
        UUID principalId,
        String action,
        String resource,
        UUID tenantId,
        UUID workspaceId,
        Set<String> grantedActions,
        UUID membershipTenantId,
        UUID membershipWorkspaceId
) {
    public AuthorizationRequest {
        grantedActions = grantedActions == null ? Set.of() : Set.copyOf(grantedActions);
    }

    /** Scope equality is mandatory; a missing membership never grants access. */
    public boolean hasVerifiedScope() {
        return principalId != null
                && tenantId != null
                && membershipTenantId != null
                && Objects.equals(tenantId, membershipTenantId)
                && (workspaceId == null || (membershipWorkspaceId != null
                && Objects.equals(workspaceId, membershipWorkspaceId)));
    }
}
