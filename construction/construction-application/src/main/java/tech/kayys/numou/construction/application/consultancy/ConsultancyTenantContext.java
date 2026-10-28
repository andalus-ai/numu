package tech.kayys.numou.construction.application.consultancy;

import java.util.Objects;
import java.util.UUID;

/**
 * Trusted request context supplied by the application/runtime boundary after
 * authentication. Do not construct this value directly from an untrusted
 * request body; tenant and actor identity must come from verified credentials
 * and tenant membership.
 */
public record ConsultancyTenantContext(String tenantId, UUID actorId) {
    public ConsultancyTenantContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant id is required");
        }
        Objects.requireNonNull(actorId, "Actor id is required");
        tenantId = tenantId.trim();
    }
}
