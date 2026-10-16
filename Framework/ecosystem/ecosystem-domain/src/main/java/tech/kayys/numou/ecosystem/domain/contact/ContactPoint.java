package tech.kayys.syirkah.ecosystem.domain.contact;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value-bearing communication endpoint model (config03.md §P4-13 #3).
 */
public record ContactPoint(
        ContactPointId id,
        ContactPointKind kind,
        String value,
        ContactPointStatus status,
        VerificationStatus verificationStatus
) implements Serializable {

    public ContactPoint {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(verificationStatus, "verificationStatus cannot be null");

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }

        value = value.trim();
    }

    public static ContactPoint create(ContactPointId id, ContactPointKind kind, String value) {
        return new ContactPoint(id, kind, value, ContactPointStatus.ACTIVE, VerificationStatus.UNVERIFIED);
    }

    public ContactPoint withStatus(ContactPointStatus newStatus) {
        return new ContactPoint(id, kind, value, newStatus, verificationStatus);
    }

    public ContactPoint withVerificationStatus(VerificationStatus newVerificationStatus) {
        return new ContactPoint(id, kind, value, status, newVerificationStatus);
    }

    public ContactPoint withValue(String newValue) {
        // Changing an endpoint value resets verification to UNVERIFIED (rule #6)
        return new ContactPoint(id, kind, newValue, status, VerificationStatus.UNVERIFIED);
    }
}
