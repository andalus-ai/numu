package tech.kayys.numou.workforce.domain.analytics;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record WorkforceDemandId(UUID value) implements DomainId<UUID> {
    public WorkforceDemandId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static WorkforceDemandId generate() {
        return new WorkforceDemandId(UUID.randomUUID());
    }

    public static WorkforceDemandId of(UUID value) {
        return new WorkforceDemandId(value);
    }
}
