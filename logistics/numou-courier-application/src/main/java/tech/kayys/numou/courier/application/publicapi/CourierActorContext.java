package tech.kayys.numou.courier.application.publicapi;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;

/**
 * Trusted execution context supplied by the host application's authentication boundary.
 *
 * <p>Never construct this value from request-body tenant or actor fields. A host must
 * derive tenant and actor identity from its authenticated principal and propagate the
 * correlation identifier from its request/message context.</p>
 */
public record CourierActorContext(TenantId tenantId, String actorReference, String correlationId) {
    public CourierActorContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        actorReference = require(actorReference, "actorReference");
        correlationId = require(correlationId, "correlationId");
    }

    private static String require(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
