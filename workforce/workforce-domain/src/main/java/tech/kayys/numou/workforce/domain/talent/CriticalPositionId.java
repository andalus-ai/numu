package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record CriticalPositionId(UUID value) implements DomainId<UUID> {
    public CriticalPositionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CriticalPositionId generate() {
        return new CriticalPositionId(UUID.randomUUID());
    }

    public static CriticalPositionId of(UUID value) {
        return new CriticalPositionId(value);
    }
}
