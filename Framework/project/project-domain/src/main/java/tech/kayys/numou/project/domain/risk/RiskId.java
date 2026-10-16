package tech.kayys.syirkah.project.domain.risk;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record RiskId(UUID value)
        implements DomainId<UUID> {

    public RiskId {
        Objects.requireNonNull(value, "Risk id cannot be null");
    }

    public static RiskId generate() {
        return new RiskId(UUID.randomUUID());
    }

    public static RiskId of(UUID value) {
        return new RiskId(value);
    }
}
