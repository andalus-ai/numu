package tech.kayys.syirkah.construction.domain.progress;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ProgressMeasurementId(UUID value) implements DomainId<UUID> {
    public ProgressMeasurementId { Objects.requireNonNull(value, "Progress measurement id cannot be null"); }
    public static ProgressMeasurementId generate() { return new ProgressMeasurementId(UUID.randomUUID()); }
    public static ProgressMeasurementId of(UUID value) { return new ProgressMeasurementId(value); }
}
