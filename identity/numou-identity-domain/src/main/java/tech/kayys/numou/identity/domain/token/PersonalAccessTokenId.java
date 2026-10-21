package tech.kayys.numou.identity.domain.token;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record PersonalAccessTokenId(UUID value) implements DomainId<UUID> {
    public PersonalAccessTokenId {
        Objects.requireNonNull(value, "PersonalAccessTokenId value cannot be null");
    }

    public static PersonalAccessTokenId newId() {
        return new PersonalAccessTokenId(UUID.randomUUID());
    }

    public static PersonalAccessTokenId of(UUID value) {
        return new PersonalAccessTokenId(value);
    }
}
