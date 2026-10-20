package tech.kayys.numou.identity.application.dto;

import java.util.Objects;

/**
 * A SCIM User write model (POST create / PUT replace), already normalized by the REST layer.
 *
 * @param userName    SCIM userName (mapped to the internal e-mail when it is one)
 * @param externalId  provider-side idempotency key (also becomes the federation-link subject)
 * @param displayName display name
 * @param email       primary e-mail; defaults to {@code userName} when it is an e-mail
 * @param active      desired active flag
 */
public record ScimUserWrite(
        String userName,
        String externalId,
        String displayName,
        String email,
        boolean active
) {

    public ScimUserWrite {
        Objects.requireNonNull(userName, "userName cannot be null");
        if (userName.isBlank()) {
            throw new IllegalArgumentException("userName cannot be blank");
        }
        displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
        email = email == null || email.isBlank() ? null : email.trim();
        externalId = externalId == null || externalId.isBlank() ? null : externalId.trim();
    }
}
