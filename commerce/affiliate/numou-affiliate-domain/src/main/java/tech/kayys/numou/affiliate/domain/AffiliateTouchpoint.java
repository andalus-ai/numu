package tech.kayys.numou.affiliate.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A validated referral interaction. subjectKey must be a tenant-scoped opaque/pseudonymous key,
 * not a raw email, phone number, or other directly identifying value.
 */
public record AffiliateTouchpoint(
        UUID id,
        String tenantId,
        UUID programId,
        UUID partnerId,
        UUID referralLinkId,
        String subjectKey,
        Instant capturedAt) {

    public AffiliateTouchpoint {
        Objects.requireNonNull(id, "id is required");
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(programId, "programId is required");
        Objects.requireNonNull(partnerId, "partnerId is required");
        Objects.requireNonNull(referralLinkId, "referralLinkId is required");
        subjectKey = required(subjectKey, "subjectKey");
        Objects.requireNonNull(capturedAt, "capturedAt is required");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
