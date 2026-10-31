package tech.kayys.numou.automotiveparts.purchasing;

import io.quarkus.security.identity.SecurityIdentity;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Resolves the operator from authenticated runtime context.
 *
 * <p>The tenant must have been verified by the authentication/tenant-selection
 * filter. The actor is taken from Quarkus SecurityIdentity, never from a
 * request header, body, or query parameter.
 */
public final class RequestContextReceiptPostingOperator implements CurrentReceiptPostingOperatorPort {
    private final RequestContextHolder requestContext;
    private final SecurityIdentity securityIdentity;

    public RequestContextReceiptPostingOperator(RequestContextHolder requestContext,
                                                SecurityIdentity securityIdentity) {
        this.requestContext = Objects.requireNonNull(requestContext, "requestContext is required");
        this.securityIdentity = Objects.requireNonNull(securityIdentity, "securityIdentity is required");
    }

    @Override
    public ReceiptPostingOperatorContext current() {
        if (securityIdentity.isAnonymous()) {
            throw new SecurityException("Authenticated receipt posting operator is required");
        }
        if (!requestContext.tenantVerified()) {
            throw new SecurityException("A verified tenant scope is required");
        }

        final String tenantId;
        final String actorId;
        try {
            tenantId = TenantId.of(requestContext.tenantId()).toString();
            // Identity's verified actor filter requires the principal subject to be a UUID.
            actorId = UUID.fromString(securityIdentity.getPrincipal().getName()).toString();
        } catch (RuntimeException invalidIdentity) {
            throw new SecurityException("Authenticated tenant and actor identifiers are invalid", invalidIdentity);
        }
        return new ReceiptPostingOperatorContext(tenantId, actorId);
    }
}
