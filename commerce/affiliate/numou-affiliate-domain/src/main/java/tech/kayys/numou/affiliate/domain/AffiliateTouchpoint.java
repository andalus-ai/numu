package tech.kayys.numou.affiliate.domain;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
/** Validated referral interaction. subjectKey must be opaque/pseudonymous, never a raw personal identifier. */
public record AffiliateTouchpoint(UUID id, String tenantId, UUID programId, UUID partnerId,
        UUID referralLinkId, String subjectKey, Instant capturedAt) {
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
        String v = value.trim();
        if (v.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return v;
    }
}
