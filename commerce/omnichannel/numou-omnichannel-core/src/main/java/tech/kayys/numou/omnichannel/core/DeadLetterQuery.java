package tech.kayys.numou.omnichannel.core;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Bounded, tenant-scoped filters for operator inspection. */
public record DeadLetterQuery(
        TenantId tenantId,
        Optional<String> providerId,
        Optional<String> errorCode,
        Optional<Instant> updatedBefore,
        int minimumAttempts,
        int limit) {

    public DeadLetterQuery {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        providerId = Objects.requireNonNull(providerId, "providerId must not be null")
                .map(value -> required(value, "providerId", 100));
        errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null")
                .map(DeadLetterQuery::safeCode);
        updatedBefore = Objects.requireNonNull(updatedBefore, "updatedBefore must not be null");
        if (minimumAttempts < 0) throw new IllegalArgumentException("minimumAttempts must not be negative");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
    }

    private static String safeCode(String value) {
        String safe = required(value, "errorCode", 100);
        if (!safe.matches("[A-Za-z0-9_.:-]+")) {
            throw new IllegalArgumentException("errorCode must be a sanitized code");
        }
        return safe;
    }

    private static String required(String value, String field, int max) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + max + " characters");
        }
        return normalized;
    }
}
