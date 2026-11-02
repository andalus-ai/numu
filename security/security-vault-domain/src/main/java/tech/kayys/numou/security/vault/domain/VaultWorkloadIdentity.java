package tech.kayys.numou.security.vault.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Verified workload identity resolved by a trusted host adapter, never accepted
 * directly from request headers or caller-supplied claims.
 */
public record VaultWorkloadIdentity(UUID tenantId, String issuer, String subject,
                                    Set<String> audiences, Set<String> scopes,
                                    Instant expiresAt) {
    public VaultWorkloadIdentity {
        Objects.requireNonNull(tenantId, "tenantId is required");
        issuer = requireText(issuer, "issuer", 256);
        subject = requireText(subject, "subject", 256);
        audiences = boundedSet(audiences, "audiences");
        scopes = boundedSet(scopes, "scopes");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }

    public boolean isValidFor(UUID tenant, Instant now) {
        return tenantId.equals(tenant) && expiresAt.isAfter(now);
    }

    private static Set<String> boundedSet(Set<String> values, String field) {
        if (values == null || values.isEmpty() || values.size() > 32)
            throw new IllegalArgumentException(field + " must contain 1..32 entries");
        for (String value : values) requireText(value, field + " entry", 256);
        return Set.copyOf(values);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(field + " must contain 1.." + max + " characters");
        return value;
    }
}
