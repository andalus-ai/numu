package tech.kayys.numou.scheduling.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.UUID;

public record ResourceAvailabilityId(UUID value) implements DomainId<UUID> {
    public static ResourceAvailabilityId of(UUID v) { return new ResourceAvailabilityId(v); }
    public static ResourceAvailabilityId generate() { return new ResourceAvailabilityId(UUID.randomUUID()); }
}
