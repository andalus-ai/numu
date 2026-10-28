package tech.kayys.numou.construction.domain.consultancy;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConsultancyEngagementId(UUID value) implements DomainId<UUID> {
    public ConsultancyEngagementId {
        Objects.requireNonNull(value, "Consultancy engagement id cannot be null");
    }

    public static ConsultancyEngagementId generate() {
        return new ConsultancyEngagementId(UUID.randomUUID());
    }

    public static ConsultancyEngagementId of(UUID value) {
        return new ConsultancyEngagementId(value);
    }
}
