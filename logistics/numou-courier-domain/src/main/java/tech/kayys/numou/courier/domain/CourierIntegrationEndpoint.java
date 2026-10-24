package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Webhook/integration metadata. Secret material must live in a secrets manager;
 * secretReference is an opaque reference, not a credential.
 */
public record CourierIntegrationEndpoint(UUID id, TenantId tenantId, String code, URI targetUri,
                                         String secretReference, Set<String> eventTypes,
                                         SignatureMode signatureMode, Status status,
                                         Instant createdAt, Instant updatedAt) {
    public enum SignatureMode { HMAC_SHA256, MUTUAL_TLS, NONE }
    public enum Status { DISABLED, ACTIVE, SUSPENDED }
    public CourierIntegrationEndpoint {
        Objects.requireNonNull(id); Objects.requireNonNull(tenantId);
        code = required(code, "code").toUpperCase(java.util.Locale.ROOT);
        Objects.requireNonNull(targetUri);
        if (!"https".equalsIgnoreCase(targetUri.getScheme()) || targetUri.getHost() == null)
            throw new IllegalArgumentException("integration target must be an absolute HTTPS URI");
        secretReference = required(secretReference, "secretReference");
        eventTypes = Set.copyOf(Objects.requireNonNull(eventTypes));
        if (eventTypes.isEmpty() || eventTypes.stream().anyMatch(v -> v == null || v.isBlank()))
            throw new IllegalArgumentException("eventTypes must contain non-blank event names");
        Objects.requireNonNull(signatureMode); Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt cannot precede createdAt");
        if (signatureMode != SignatureMode.NONE && secretReference.isBlank())
            throw new IllegalArgumentException("signed integrations require a secret/certificate reference");
    }
    public boolean accepts(String eventType) { return status == Status.ACTIVE && eventTypes.contains(eventType); }
    private static String required(String v, String field) {
        Objects.requireNonNull(v, field); String s=v.trim();
        if (s.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return s;
    }
}