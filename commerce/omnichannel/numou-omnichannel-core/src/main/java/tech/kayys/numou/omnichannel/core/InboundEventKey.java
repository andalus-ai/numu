package tech.kayys.numou.omnichannel.core;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.util.Objects;

/** Durable uniqueness scope for a provider event. */
public record InboundEventKey(TenantId tenantId, String providerId, String providerAccountId, String externalEventId) {
    public InboundEventKey {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        providerId = required(providerId, "providerId", 100);
        providerAccountId = required(providerAccountId, "providerAccountId", 200);
        externalEventId = required(externalEventId, "externalEventId", 250);
    }
    public static InboundEventKey from(InboundEventEnvelope envelope, String providerId) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        return new InboundEventKey(envelope.context().tenantId(), providerId,
                envelope.context().providerAccountId(), envelope.externalEventId());
    }
    private static String required(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        return normalized;
    }
}