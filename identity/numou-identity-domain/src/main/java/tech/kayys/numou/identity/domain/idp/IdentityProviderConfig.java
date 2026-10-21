package tech.kayys.numou.identity.domain.idp;

import java.time.Instant;
import java.util.Objects;

/**
 * Identity provider configuration for federation (OIDC, LDAP, SAML, LOCAL).
 */
public final class IdentityProviderConfig {

    private final IdentityProviderConfigId id;
    private final String name;
    private final IdentityProviderType type;
    private final String connectionConfigJson;
    private boolean enabled;
    private final String scimEndpoint;
    private final Instant createdAt;

    private IdentityProviderConfig(
            IdentityProviderConfigId id,
            String name,
            IdentityProviderType type,
            String connectionConfigJson,
            boolean enabled,
            String scimEndpoint,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = requireText(name, "name");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.connectionConfigJson = connectionConfigJson;
        this.enabled = enabled;
        this.scimEndpoint = scimEndpoint;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
    }

    public static IdentityProviderConfig create(
            IdentityProviderConfigId id,
            String name,
            IdentityProviderType type,
            String connectionConfigJson,
            String scimEndpoint,
            Instant occurredAt
    ) {
        return new IdentityProviderConfig(
                id,
                name,
                type,
                connectionConfigJson,
                true,
                scimEndpoint,
                occurredAt
        );
    }

    public static IdentityProviderConfig reconstitute(
            IdentityProviderConfigId id,
            String name,
            IdentityProviderType type,
            String connectionConfigJson,
            boolean enabled,
            String scimEndpoint,
            Instant createdAt
    ) {
        return new IdentityProviderConfig(
                id,
                name,
                type,
                connectionConfigJson,
                enabled,
                scimEndpoint,
                createdAt
        );
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }

    public IdentityProviderConfigId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public IdentityProviderType type() {
        return type;
    }

    public String connectionConfigJson() {
        return connectionConfigJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String scimEndpoint() {
        return scimEndpoint;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}
