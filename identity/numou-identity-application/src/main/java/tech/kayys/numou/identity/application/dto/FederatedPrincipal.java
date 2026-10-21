package tech.kayys.numou.identity.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * The internal principal a federated subject (OIDC, LDAP, or SAML) resolved to.
 *
 * @param userId      internal user id
 * @param displayName display name for the issued token
 * @param provisioned true when this login created the internal user (JIT provisioning)
 */
public record FederatedPrincipal(UUID userId, String displayName, boolean provisioned) {

    public FederatedPrincipal {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
    }
}
