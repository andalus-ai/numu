package tech.kayys.numou.ecosystem.domain.party;

import java.util.Objects;

/**
 * Stable identity-provider subject reference linked to a Party.
 *
 * The issuer scopes the subject identifier. A subject is never interpreted as
 * a Numou UserId or PartyId, even when its textual value resembles a UUID.
 */
public record IdentitySubjectRef(String issuer, String subject) {

    public IdentitySubjectRef {
        issuer = requireText(issuer, "issuer");
        subject = requireText(subject, "subject");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return normalized;
    }
}
