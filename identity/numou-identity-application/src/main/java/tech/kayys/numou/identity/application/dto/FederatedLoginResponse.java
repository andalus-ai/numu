package tech.kayys.numou.identity.application.dto;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Result of a completed federated login: an internal Numou access token plus
 * enough detail for the client/audit log to know what happened.
 *
 * @param token       short-lived internal access JWT (never the provider's token)
 * @param provisioned true when this login created the internal user (JIT provisioning)
 * @param userId      internal user id
 * @param groups      provider groups the subject belongs to
 */
public record FederatedLoginResponse(
        AccessTokenResponse token,
        boolean provisioned,
        UUID userId,
        Set<String> groups
) {

    public FederatedLoginResponse {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        groups = groups == null ? Set.of() : Set.copyOf(groups);
    }
}
