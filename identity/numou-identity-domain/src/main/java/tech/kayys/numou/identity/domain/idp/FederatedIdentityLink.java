package tech.kayys.numou.identity.domain.idp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Link between an external identity provider subject and an internal user.
 *
 * <p>One row per {@code (providerId, subject)}: the same person logging in through the same
 * provider always resolves to the same internal user (Just-In-Time provisioning runs once).
 *
 * @param id           link identifier
 * @param providerId   identity provider configuration id
 * @param subject      provider-scoped unique subject
 * @param userId       internal user this subject is linked to
 * @param email        e-mail observed at link time (informational)
 * @param lastLoginAt  last successful federated authentication
 */
public record FederatedIdentityLink(
        UUID id,
        UUID providerId,
        String subject,
        UUID userId,
        String email,
        Instant lastLoginAt
) {

    public FederatedIdentityLink {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(subject, "subject cannot be null");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject cannot be blank");
        }
        Objects.requireNonNull(userId, "userId cannot be null");
        email = email == null || email.isBlank() ? null : email.trim();
    }

    public static FederatedIdentityLink create(
            UUID providerId,
            String subject,
            UUID userId,
            String email,
            Instant occurredAt
    ) {
        return new FederatedIdentityLink(
                UUID.randomUUID(), providerId, subject, userId, email, occurredAt
        );
    }

    public FederatedIdentityLink withLastLogin(Instant occurredAt) {
        return new FederatedIdentityLink(id, providerId, subject, userId, email, occurredAt);
    }
}
