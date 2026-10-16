package tech.kayys.syirkah.construction.domain.quality;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record InspectionRequestId(UUID value) implements DomainId<UUID> {
    public InspectionRequestId { Objects.requireNonNull(value); }
    public static InspectionRequestId generate() { return new InspectionRequestId(UUID.randomUUID()); }
    public static InspectionRequestId of(UUID value) { return new InspectionRequestId(value); }
}
