package tech.kayys.numou.identity.adapter.inbound.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.service.ScopedRoleAssignmentService;
import tech.kayys.numou.security.domain.authorization.AccessRequest;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.domain.identifier.PrincipalId;
import tech.kayys.numou.security.domain.valueobject.ActionType;
import tech.kayys.numou.security.spi.port.AuthorizationServicePort;
import tech.kayys.numou.security.spi.port.TenantMembershipResolver;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deny-by-default identity administration authorization.
 */
@ApplicationScoped
public class IdentityManagementGuard {

    private final SecurityIdentity identity;
    private final ScopedRoleAssignmentService assignments;
    private final Optional<String> configuredScope;

    @Inject
    TenantMembershipResolver memberships;

    @Inject
    AuthorizationServicePort authorization;

    @ConfigProperty(name = "numou.security.tenant-claim", defaultValue = "numou_tenant_id")
    String tenantAttribute;

    @ConfigProperty(name = "numou.identity.authorization.timeout-millis", defaultValue = "2000")
    long authorizationTimeoutMillis;

    @Inject
    public IdentityManagementGuard(
            SecurityIdentity identity,
            ScopedRoleAssignmentService assignments,
            @ConfigProperty(name = "numou.identity.management-scope-path") Optional<String> configuredScope
    ) {
        this.identity = identity;
        this.assignments = assignments;
        this.configuredScope = configuredScope;
    }

    public UUID actor() {
        if (identity.isAnonymous()) {
            throw new WebApplicationException("Authentication required", 401);
        }
        return UUID.fromString(VerifiedActorFilter.canonicalActor(identity.getPrincipal().getName()));
    }

    public String scope() {
        String value = configuredScope.orElse("").trim();
        if (value.isEmpty()) {
            throw new WebApplicationException("Management scope not configured", 503);
        }
        return value;
    }

    /**
     * Authenticates tenant membership and tenant-scoped permission using only
     * the tenant claim already attached to the verified SecurityIdentity.
     */
    public Uni<TenantAuthorizationContext> requireTenantAuthorization(String resourceType, ActionType action) {
        UUID actor = actor();
        Object rawTenant = identity.getAttribute(tenantAttribute);
        if (!(rawTenant instanceof String tenantClaim) || tenantClaim.isBlank()) {
            throw new WebApplicationException("Verified tenant context required", 403);
        }
        if (authorizationTimeoutMillis < 100 || authorizationTimeoutMillis > 10_000) {
            throw new WebApplicationException("Authorization configuration is invalid", 503);
        }

        final TenantId tenant;
        try {
            tenant = TenantId.of(tenantClaim.trim());
        } catch (RuntimeException invalidTenant) {
            throw new WebApplicationException("Verified tenant context is invalid", 403);
        }
        Principal principal = Principal.of(
                PrincipalId.of(actor), identity.getPrincipal().getName(), tenant);
        Duration timeout = Duration.ofMillis(authorizationTimeoutMillis);

        return memberships.isMember(principal, tenant)
                .ifNoItem().after(timeout).fail()
                .onFailure().transform(error ->
                        new WebApplicationException("Tenant membership verification unavailable", 503))
                .chain(active -> {
                    if (!Boolean.TRUE.equals(active)) {
                        return Uni.createFrom().failure(
                                new WebApplicationException("Active tenant membership required", 403));
                    }
                    AccessRequest request = AccessRequest.of(principal, tenant, action, resourceType);
                    return authorization.authorize(request)
                            .ifNoItem().after(timeout).fail()
                            .onFailure().transform(error ->
                                    new WebApplicationException("Tenant authorization unavailable", 503))
                            .map(decision -> {
                                if (decision == null || !decision.isAllowed()) {
                                    throw new WebApplicationException("Forbidden", 403);
                                }
                                return new TenantAuthorizationContext(actor, tenant);
                            });
                });
    }

    /** Compatibility convenience for callers that only need the actor. */
    public Uni<UUID> requireTenantPermission(String resourceType, ActionType action) {
        return requireTenantAuthorization(resourceType, action).map(TenantAuthorizationContext::actorId);
    }

    public Uni<UUID> require(String permission) {
        UUID actor = actor();
        return assignments.resolveEffectivePermissions("USER", actor, scope())
                .onFailure().recoverWithItem(Set.of())
                .map(granted -> {
                    if (!granted.contains(permission)) {
                        throw new WebApplicationException("Forbidden", 403);
                    }
                    return actor;
                });
    }

    public Uni<UUID> requireWithinScope(String permission, String requestedScope) {
        String root = scope();
        if (requestedScope == null || !(requestedScope.equals(root) || requestedScope.startsWith(root + "."))) {
            throw new WebApplicationException("Scope outside management boundary", 403);
        }
        return require(permission);
    }
}
