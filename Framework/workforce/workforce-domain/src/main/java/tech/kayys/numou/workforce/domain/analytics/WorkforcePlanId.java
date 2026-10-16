package tech.kayys.numou.workforce.domain.analytics;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record WorkforcePlanId(UUID value) implements DomainId<UUID> {
    public WorkforcePlanId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static WorkforcePlanId generate() {
        return new WorkforcePlanId(UUID.randomUUID());
    }

    public static WorkforcePlanId of(UUID value) {
        return new WorkforcePlanId(value);
    }
}
