package tech.kayys.numou.identity.application.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A SCIM User projected from an internal user + its federation link (if any).
 */
public record ScimUser(
        UUID id,
        String userName,
        String externalId,
        String displayName,
        String email,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public ScimUser {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(userName, "userName cannot be null");
    }
}
