package tech.kayys.numou.identity.domain.idp;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record IdentityProviderConfigId(UUID value) implements DomainId<UUID> {
    public IdentityProviderConfigId {
        Objects.requireNonNull(value, "IdentityProviderConfigId value cannot be null");
    }

    public static IdentityProviderConfigId newId() {
        return new IdentityProviderConfigId(UUID.randomUUID());
    }

    public static IdentityProviderConfigId of(UUID value) {
        return new IdentityProviderConfigId(value);
    }
}
