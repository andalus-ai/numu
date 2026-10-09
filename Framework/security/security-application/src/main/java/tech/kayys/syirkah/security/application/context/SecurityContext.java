package tech.kayys.syirkah.security.application.context;

import tech.kayys.syirkah.security.domain.authorization.Principal;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The <em>verified</em> identity of the current request: who is acting, inside
 * which tenant, with which roles.
 *
 * <p>Built only after token validation and tenant resolution. Application code
 * receives this instead of raw headers, so "trust the client's X-Tenant-Id" is
 * not expressible.
 */
public record SecurityContext(Principal principal, UUID tenantId, Set<String> roles, String clientIp) {

    public SecurityContext {
        Objects.requireNonNull(principal, "principal cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        clientIp = clientIp == null ? "" : clientIp;
    }

    public String actor() {
        return principal.handle();
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
