package tech.kayys.syirkah.security.domain.authorization;

import java.util.Objects;
import java.util.Set;

/**
 * Domain service that decides an {@link AccessRequest} given the
 * permissions granted to the principal (base01.md §P1-17, security02.md).
 *
 * <p>Order of the checks is deliberate and stable:
 * <pre>
 *   1. tenant membership  - a non-member is denied before anything else
 *   2. permission grant    - the verb/resource pair must be granted
 *   3. resource scope      - a specific resource id is only allowed when
 *                            it was included in the grant
 * </pre>
 */
public final class PermissionEvaluator {

    /** Grants of the principal being evaluated. */
    private final Set<Permission> granted;

    /** Resource ids the grant is narrowed to; empty means "all". */
    private final Set<String> scopedResourceIds;

    public PermissionEvaluator(Set<Permission> granted, Set<String> scopedResourceIds) {
        this.granted = granted == null ? Set.of() : Set.copyOf(granted);
        this.scopedResourceIds = scopedResourceIds == null
                ? Set.of()
                : Set.copyOf(scopedResourceIds);
    }

    /** Evaluates a request. Never throws for denials - denial is a result. */
    public AccessDecision decide(AccessRequest request) {
        Objects.requireNonNull(request, "request cannot be null");

        if (!request.principal().isMemberOf(request.tenantId())) {
            return AccessDecision.deny("Principal is not a member of tenant " + request.tenantId());
        }

        final var permission = Permission.of(request.resourceType(), request.action().name());
        if (!granted.contains(permission)) {
            return AccessDecision.deny(
                    "Permission " + permission.action() + " on " + permission.resource()
                            + " is not granted");
        }

        if (!request.resourceId().isBlank()
                && !scopedResourceIds.isEmpty()
                && !scopedResourceIds.contains(request.resourceId())) {
            return AccessDecision.deny(
                    "Permission is scoped and does not cover " + request.resourceId());
        }

        return AccessDecision.allow(
                "Member of tenant " + request.tenantId()
                        + " with permission " + permission.action()
                        + " on " + permission.resource());
    }

    /** Convenience factory for an all-resources grant. Empty grants evaluate to DENY on decide(). */
    public static PermissionEvaluator with(Set<Permission> granted) {
        return new PermissionEvaluator(granted == null ? Set.of() : granted, Set.of());
    }
}
