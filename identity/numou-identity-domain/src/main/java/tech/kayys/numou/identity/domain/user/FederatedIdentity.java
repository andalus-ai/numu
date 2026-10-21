package tech.kayys.numou.identity.domain.user;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Identity assertions an external identity provider made about a user
 * (OIDC ID token / OAuth2 profile, SAML assertion, LDAP entry).
 *
 * <p>Pure value object: it carries what the provider said, never how Numou
 * should act on it - group-to-role mapping is an application concern.
 *
 * @param providerId  identity provider configuration id (string form)
 * @param subject     provider-scoped unique subject id ({@code sub})
 * @param email       e-mail address asserted by the provider (may be blank for SCIM-less IdPs)
 * @param displayName display name asserted by the provider
 * @param groups      group/role claims asserted by the provider
 * @param claims      all other claims, kept for audit/debugging (never logged verbatim)
 */
public record FederatedIdentity(
        String providerId,
        String subject,
        String email,
        String displayName,
        Set<String> groups,
        Map<String, String> claims
) {

    public FederatedIdentity {
        Objects.requireNonNull(providerId, "providerId cannot be null");
        Objects.requireNonNull(subject, "subject cannot be null");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject cannot be blank");
        }
        email = email == null ? "" : email.trim();
        displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
        groups = groups == null ? Set.of() : Set.copyOf(groups);
        claims = claims == null ? Map.of() : Map.copyOf(claims);
    }
}
