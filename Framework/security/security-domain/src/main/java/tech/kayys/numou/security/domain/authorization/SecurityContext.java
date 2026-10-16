package tech.kayys.syirkah.security.domain.authorization;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.security.domain.authentication.AuthenticationInfo;
import tech.kayys.syirkah.security.domain.authentication.AuthenticationMethod;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical runtime security context capturing the active authenticated principal,
 * tenant scope, authentication metadata, and effective permissions (security01.md, security02.md).
 */
public record SecurityContext(
        Principal principal,
        TenantId tenantId,
        AuthenticationInfo authentication,
        Set<Permission> permissions) {

    public SecurityContext {
        Objects.requireNonNull(principal, "principal cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        authentication = authentication == null
                ? AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now())
                : authentication;
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);

        if (!authentication.authenticated()) {
            throw new IllegalArgumentException("SecurityContext requires authentication");
        }
    }

    public SecurityContext(Principal principal, TenantId tenantId, AuthenticationInfo authentication) {
        this(principal, tenantId, authentication, Set.of());
    }

    public SecurityContext(Principal principal, TenantId tenantId, Set<Permission> permissions) {
        this(principal, tenantId, AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()), permissions);
    }

    public SecurityContext(Principal principal, TenantId tenantId) {
        this(principal, tenantId, AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()), Set.of());
    }

    public boolean authenticated() {
        return authentication != null && authentication.authenticated();
    }

    public boolean tenantMember() {
        return principal.isMemberOf(tenantId);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
