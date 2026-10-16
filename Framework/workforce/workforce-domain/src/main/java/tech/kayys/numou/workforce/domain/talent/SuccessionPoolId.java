package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record SuccessionPoolId(UUID value) implements DomainId<UUID> {
    public SuccessionPoolId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SuccessionPoolId generate() {
        return new SuccessionPoolId(UUID.randomUUID());
    }

    public static SuccessionPoolId of(UUID value) {
        return new SuccessionPoolId(value);
    }
}
