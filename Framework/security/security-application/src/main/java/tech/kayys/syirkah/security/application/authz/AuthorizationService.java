package tech.kayys.syirkah.security.application.authz;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.security.application.context.SecurityContext;
import tech.kayys.syirkah.security.domain.audit.AuditEntry;
import tech.kayys.syirkah.security.domain.authorization.AccessDecision;
import tech.kayys.syirkah.security.domain.authorization.AccessRequest;
import tech.kayys.syirkah.security.domain.authorization.PermissionEvaluator;
import tech.kayys.syirkah.security.domain.valueobject.ActionType;
import tech.kayys.syirkah.security.spi.port.AuditPort;

import java.util.Objects;

/**
 * Entry point for "may this request do that?".
 *
 * <p>Wraps the pure {@link PermissionEvaluator} with grant loading and audit.
 * Denials are audited (auditors want to see refused attempts); a failing audit
 * sink never turns a denial into an allow.
 */
public final class AuthorizationService {

    private final GrantResolver grants;
    private final AuditPort audit;

    public AuthorizationService(GrantResolver grants, AuditPort audit) {
        this.grants = Objects.requireNonNull(grants, "grants cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
    }

    public Uni<AccessDecision> decide(SecurityContext ctx, ActionType action, String resourceType, String resourceId) {
        return grants.grantsFor(ctx).map(granted -> {
            final var evaluator = new PermissionEvaluator(granted, null);
            return evaluator.decide(new AccessRequest(
                    ctx.principal(), tech.kayys.syirkah.foundation.domain.tenant.TenantId.of(ctx.tenantId()), action, resourceType, resourceId, ""));
        });
    }

    /** Completes when allowed; fails with {@link AccessDeniedException} otherwise. */
    public Uni<Void> require(SecurityContext ctx, ActionType action, String resourceType, String resourceId) {
        return decide(ctx, action, resourceType, resourceId).flatMap(decision -> {
            if (decision.isAllowed()) {
                return Uni.createFrom().voidItem();
            }
            final var entry = AuditEntry.of(
                    ctx.actor(), ctx.tenantId().toString(), "ACCESS_DENIED:" + action,
                    resourceType, resourceId, "");
            return audit.append(entry)
                    .onFailure().recoverWithItem((Void) null)
                    .flatMap(ignored -> Uni.<Void>createFrom().failure(new AccessDeniedException(decision)));
        });
    }

    public Uni<Void> require(SecurityContext ctx, ActionType action, String resourceType) {
        return require(ctx, action, resourceType, "");
    }

    /** Records a sensitive <em>allowed</em> action (void, comp, waiver, refund). */
    public Uni<Void> auditPerformed(SecurityContext ctx, String action, String resource, String resourceId, String detail) {
        final var entry = new AuditEntry(ctx.actor(), ctx.tenantId().toString(), action, resource, resourceId,
                java.time.Instant.now().toString(), "", detail);
        return audit.append(entry);
    }
}
