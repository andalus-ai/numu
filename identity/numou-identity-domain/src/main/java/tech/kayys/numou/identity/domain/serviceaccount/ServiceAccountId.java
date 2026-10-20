package tech.kayys.numou.identity.domain.serviceaccount;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record ServiceAccountId(UUID value) implements DomainId<UUID> {
    public ServiceAccountId {
        Objects.requireNonNull(value, "ServiceAccountId value cannot be null");
    }

    public static ServiceAccountId newId() {
        return new ServiceAccountId(UUID.randomUUID());
    }

    public static ServiceAccountId of(UUID value) {
        return new ServiceAccountId(value);
    }
}
