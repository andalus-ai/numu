package tech.kayys.numou.construction.domain.consultancy;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConsultancyDeliverableId(UUID value) implements DomainId<UUID> {
    public ConsultancyDeliverableId {
        Objects.requireNonNull(value, "Deliverable id cannot be null");
    }

    public static ConsultancyDeliverableId generate() {
        return new ConsultancyDeliverableId(UUID.randomUUID());
    }

    public static ConsultancyDeliverableId of(UUID value) {
        return new ConsultancyDeliverableId(value);
    }
}
