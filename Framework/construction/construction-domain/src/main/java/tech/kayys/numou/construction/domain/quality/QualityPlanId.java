package tech.kayys.syirkah.construction.domain.quality;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record QualityPlanId(UUID value) implements DomainId<UUID> {
    public QualityPlanId { Objects.requireNonNull(value); }
    public static QualityPlanId generate() { return new QualityPlanId(UUID.randomUUID()); }
    public static QualityPlanId of(UUID value) { return new QualityPlanId(value); }
}
