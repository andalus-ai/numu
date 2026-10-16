package tech.kayys.numou.workforce.domain.analytics;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record WorkforceSnapshotId(UUID value) implements DomainId<UUID> {
    public WorkforceSnapshotId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static WorkforceSnapshotId generate() {
        return new WorkforceSnapshotId(UUID.randomUUID());
    }

    public static WorkforceSnapshotId of(UUID value) {
        return new WorkforceSnapshotId(value);
    }
}
