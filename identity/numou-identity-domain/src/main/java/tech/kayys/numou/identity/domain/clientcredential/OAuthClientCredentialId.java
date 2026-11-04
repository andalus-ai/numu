package tech.kayys.numou.identity.domain.clientcredential;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record OAuthClientCredentialId(UUID value) implements DomainId<UUID> {
    public OAuthClientCredentialId {
        Objects.requireNonNull(value, "OAuthClientCredentialId value cannot be null");
    }

    public static OAuthClientCredentialId newId() {
        return new OAuthClientCredentialId(UUID.randomUUID());
    }

    public static OAuthClientCredentialId of(UUID value) {
        return new OAuthClientCredentialId(value);
    }
}
