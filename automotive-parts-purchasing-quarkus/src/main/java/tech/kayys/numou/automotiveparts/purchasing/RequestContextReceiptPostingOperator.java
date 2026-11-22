package tech.kayys.numou.automotiveparts.purchasing;

import io.quarkus.security.identity.SecurityIdentity;
import tech.kayys.numou.foundation.adapter.context.RequestContextHolder;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.UUID;

/** Resolves the operator from the authenticated, verified request context. */
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
        try {
            String tenantId = TenantId.of(requestContext.tenantId()).toString();
            String actorId = UUID.fromString(securityIdentity.getPrincipal().getName()).toString();
            return new ReceiptPostingOperatorContext(tenantId, actorId);
        } catch (RuntimeException invalidIdentity) {
            throw new SecurityException("Authenticated tenant and actor identifiers are invalid", invalidIdentity);
        }
    }
}
