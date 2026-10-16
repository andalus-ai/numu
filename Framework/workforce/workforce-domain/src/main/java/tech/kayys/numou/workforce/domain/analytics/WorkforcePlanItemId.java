package tech.kayys.numou.workforce.domain.analytics;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record WorkforcePlanItemId(UUID value) implements DomainId<UUID> {
    public WorkforcePlanItemId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static WorkforcePlanItemId generate() {
        return new WorkforcePlanItemId(UUID.randomUUID());
    }

    public static WorkforcePlanItemId of(UUID value) {
        return new WorkforcePlanItemId(value);
    }
}
