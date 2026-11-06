package tech.kayys.numou.identity.application.port;

import java.util.UUID;

/** Issues narrowly purposed, tenant-aware human authentication credentials. */
public interface TenantTokenIssuerPort {
    String issueTenantSelectionToken(UUID principalId, String displayName);
    String issueTenantAccessToken(UUID principalId, String displayName, UUID tenantId);
}
