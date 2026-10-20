package tech.kayys.numou.identity.adapter.inbound.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.identity.application.service.ScopedRoleAssignmentService;

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
