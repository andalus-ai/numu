package tech.kayys.syirkah.security.domain.authorization;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;

import java.util.Objects;
import java.util.UUID;

/**
 * The full context of an authorization decision (base01.md §P1-17, security01.md §3.1).
 *
 * <p>The example from the plan, made executable:
 *
 * <pre>
 *   Can John update Shipment #123 for Tenant ABC?
 * </pre>
 *
 * @param principal    who is asking
 * @param tenantId     in which tenant scope
 * @param action       what they want to do
 * @param resourceType of which domain concept
 * @param resourceId   which specific instance (nullable = any)
 * @param context      optional extra conditions (time window, ip, ...)
 */
public record AccessRequest(
        Principal principal,
        TenantId tenantId,
        ActionType action,
        String resourceType,
        String resourceId,
        String context) {

    public AccessRequest {
        Objects.requireNonNull(principal, "principal cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(resourceType, "resourceType cannot be null");
        resourceId = resourceId == null ? "" : resourceId;
        context = context == null ? "" : context;
    }

    public static AccessRequest of(
            Principal principal,
            TenantId tenantId,
            ActionType action,
            String resourceType) {
        return new AccessRequest(principal, tenantId, action, resourceType, "", "");
    }

    public static AccessRequest of(
            Principal principal,
            UUID tenantId,
            ActionType action,
            String resourceType) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        return new AccessRequest(principal, TenantId.of(tenantId), action, resourceType, "", "");
    }
}
