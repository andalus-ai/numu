package tech.kayys.numou.security.socialgraph;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.security.adapter.CurrentSecurity;
import tech.kayys.numou.security.application.context.SecurityContextProvider;
import tech.kayys.numou.security.application.authz.RolePermissionMatrix;
import tech.kayys.numou.security.domain.authentication.AuthenticationInfo;
import tech.kayys.numou.security.domain.authentication.AuthenticationMethod;
import tech.kayys.numou.security.domain.authorization.SecurityContext;

import java.time.Instant;
import java.util.Optional;

/**
 * Bridges the request-scoped, token-verified security filter context to the
 * canonical security-domain context consumed by capability authorization.
 */
@ApplicationScoped
public class CurrentRequestSecurityContextProvider implements SecurityContextProvider {
    private final CurrentSecurity current;
    private final RolePermissionMatrix permissions;

    @Inject
    public CurrentRequestSecurityContextProvider(CurrentSecurity current, RolePermissionMatrix permissions) {
        this.current = current;
        this.permissions = permissions;
    }

    @Override
    public Optional<SecurityContext> current() {
        try {
            var request = current.require();
            return Optional.of(new SecurityContext(
                    request.principal(),
                    TenantId.of(request.tenantId()),
                    AuthenticationInfo.authenticated(AuthenticationMethod.OIDC, Instant.now()),
                    permissions.permissionsFor(request.roles())));
        } catch (IllegalStateException absentRequestContext) {
            return Optional.empty();
        }
    }

    @Override
    public SecurityContext require() {
        return current().orElseThrow(() -> new IllegalStateException(
                "No verified security context is available for this request"));
    }
}
