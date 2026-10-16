package tech.kayys.syirkah.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.identity.application.port.AuthorizationException;
import tech.kayys.syirkah.identity.application.port.AuthorizationPort;
import tech.kayys.syirkah.identity.application.port.CurrentPrincipalPort;
import tech.kayys.syirkah.identity.application.port.TenantMembershipPort;
import tech.kayys.syirkah.identity.application.security.AuthorizationRequirement;

import java.util.Objects;

public final class AuthorizationService {
    private final CurrentPrincipalPort currentPrincipal;
    private final TenantMembershipPort membership;
    private final AuthorizationPort authorization;

    public AuthorizationService(
            CurrentPrincipalPort currentPrincipal,
            TenantMembershipPort membership,
            AuthorizationPort authorization
    ) {
        this.currentPrincipal = Objects.requireNonNull(currentPrincipal);
        this.membership = Objects.requireNonNull(membership);
        this.authorization = Objects.requireNonNull(authorization);
    }

    public Uni<Void> require(String tenantId, AuthorizationRequirement requirement) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(requirement, "requirement cannot be null");

        return currentPrincipal.current().flatMap(principal -> {
            if (principal == null || !principal.authenticated()) {
                return Uni.createFrom().failure(new AuthorizationException("authentication.required"));
            }
            return membership.isMember(principal, tenantId).flatMap(member -> {
                    if (!member) {
                        return Uni.createFrom().failure(
                                new AuthorizationException("tenant.access")
                        );
                    }
                    return authorization.require(principal, tenantId, requirement);
                });
        });
    }
}
